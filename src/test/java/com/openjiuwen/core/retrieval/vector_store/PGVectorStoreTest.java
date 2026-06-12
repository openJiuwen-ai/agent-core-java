/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.vector_fields.PGVectorField;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code PGVectorStore} tests around
 * {@code openjiuwen/core/retrieval/vector_store/pg_store.py}.
 */
class PGVectorStoreTest {

    @Test
    void constructorKeepsPythonDefaultFieldsAndPgVectorFieldConfig() {
        PGVectorField field = new PGVectorField();
        field.setVectorField("embedding_vec");
        field.setM(8);

        PGVectorStore store = new PGVectorStore(
                config("collection_a", "cosine"),
                "postgresql+asyncpg://user:pass@localhost/db",
                "content",
                field,
                "sparse_vector",
                "metadata",
                "document_id"
        );

        assertThat(store.getCollectionName()).isEqualTo("collection_a");
        assertThat(store.getTextField()).isEqualTo("content");
        assertThat(store.getVectorField()).isEqualTo("embedding_vec");
        assertThat(store.getVectorFieldConfig().getM()).isEqualTo(8);
        assertThat(store.getDocIdField()).isEqualTo("document_id");
        assertThat(store.getDistanceMetric()).isEqualTo("cosine");
    }

    @Test
    void addBootstrapsTableAndBatchesUpserts() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddl = mock(Statement.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement upsertStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(ddl);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT EXISTS") ? existsStatement : upsertStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(false);

        PGVectorStore store = new TestPGVectorStore(config("kb_chunks", "cosine"), dataSource);

        store.add(List.of(
                Map.of("id", "1", "content", "a", "embedding", List.of(1.0d, 0.0d), "document_id", "doc-1"),
                Map.of("id", "2", "content", "b", "embedding", List.of(0.0d, 1.0d), "chunk_id", "chunk-2"),
                Map.of("id", "3", "content", "c", "embedding", List.of(0.5d, 0.5d))
        ), 2, Map.of()).join();

        verify(upsertStatement, times(2)).executeBatch();

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(ddl, times(3)).execute(ddlCaptor.capture());
        assertThat(ddlCaptor.getAllValues()).anyMatch(sql -> sql.contains("CREATE EXTENSION IF NOT EXISTS vector"));
        assertThat(ddlCaptor.getAllValues()).anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertThat(ddlCaptor.getAllValues()).anyMatch(sql -> sql.contains("USING hnsw"));
    }

    @Test
    void searchNormalizesCosineDistanceAndCarriesMetadata() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement searchStatement = mock(PreparedStatement.class);
        ResultSet searchResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT EXISTS") ? existsStatement : searchStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(true);
        when(searchStatement.executeQuery()).thenReturn(searchResult);
        when(searchResult.next()).thenReturn(true, false);
        when(searchResult.getString("id")).thenReturn("row-1");
        when(searchResult.getString("content")).thenReturn("hello world");
        when(searchResult.getObject("metadata")).thenReturn(Map.of("document_id", "doc-1", "source", "unit"));
        when(searchResult.getDouble("raw_score")).thenReturn(0.2d);

        PGVectorStore store = new TestPGVectorStore(config("kb_chunks", "cosine"), dataSource);

        List<RetrievalResult> results = store.search(
                List.of(1.0d, 0.0d),
                3,
                VectorStore.VectorStoreFilter.ofMap(Map.of("source", "unit")),
                Map.of()
        ).join();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).isEqualTo("hello world");
        assertThat(results.getFirst().getScore()).isEqualTo(0.8d);
        assertThat(results.getFirst().getDocId()).isEqualTo("doc-1");
        assertThat(results.getFirst().getChunkId()).isEqualTo("row-1");
        assertThat(results.getFirst().getMetadata()).containsEntry("raw_score", 0.2d);
    }

    @Test
    void deleteRequiresIdsAndUsesIdPredicate() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT EXISTS") ? existsStatement : deleteStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(true);
        when(deleteStatement.executeUpdate()).thenReturn(1);

        PGVectorStore store = new TestPGVectorStore(config("kb_chunks", "cosine"), dataSource);

        assertThat(store.delete(List.of(), VectorStore.DeleteFilter.ofExpression("id = '1'"), Map.of()).join()).isFalse();
        assertThat(store.delete(List.of("1"), VectorStore.DeleteFilter.none(), Map.of()).join()).isTrue();
    }

    @Test
    void hybridSearchUsesRrfAndDropsTemporaryMetadataId() {
        PGVectorStore store = new TestPGVectorStore(config("kb_chunks", "cosine"), mock(DataSource.class)) {
            @Override
            public CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                                   int topK,
                                                                   VectorStoreFilter filters,
                                                                   Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(List.of(
                        new RetrievalResult("shared text", 0.9d, Map.of("id", "metadata-id", "document_id", "doc-1"), null, "row-1")
                ));
            }

            @Override
            public CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                                         int topK,
                                                                         VectorStoreFilter filters,
                                                                         Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(List.of(
                        new RetrievalResult("sparse text", 0.5d, Map.of(), null, "row-2")
                ));
            }
        };

        List<RetrievalResult> results = store.hybridSearch(
                "query",
                List.of(0.1d, 0.2d),
                2,
                0.5d,
                VectorStore.VectorStoreFilter.none(),
                Map.of()
        ).join();

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().getChunkId()).isEqualTo("metadata-id");
        assertThat(results.getFirst().getDocId()).isEqualTo("doc-1");
        assertThat(results.getFirst().getMetadata()).doesNotContainKey("id");
    }

    @Test
    void dimensionAbovePgVectorLimitIsRejected() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(false);

        PGVectorStore store = new TestPGVectorStore(config("kb_chunks", "cosine"), dataSource);
        List<Double> largeVector = java.util.Collections.nCopies(2001, 0.1d);

        assertThatThrownBy(() -> store.add(List.of(Map.of("id", "1", "embedding", largeVector)), 1, Map.of()).join())
                .hasRootCauseInstanceOf(BaseError.class)
                .hasMessageContaining("pgvector only supports vector dimensions up to 2000");
    }

    private static VectorStoreConfig config(String collectionName, String metric) {
        return new VectorStoreConfig(StoreType.PGVECTOR, "test_db", collectionName, metric);
    }

    private static class TestPGVectorStore extends PGVectorStore {

        private TestPGVectorStore(VectorStoreConfig config, DataSource dataSource) {
            super(config, dataSource);
        }

        @Override
        protected void registerVectorTypes(Connection connection) throws SQLException {
        }
    }
}
