/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class PGVectorStoreTest {
    @Test
    void constructorAndDimensionValidationRejectInvalidPgConfiguration() {
        assertThrows(BaseError.class,
                () -> new PGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                        "postgresql://localhost/test_db", null, null, "hybrid", Map.of()));

        assertThrows(BaseError.class,
                () -> new PGVectorStore(new VectorStoreConfig("pgvector", "configured_db", "kb_chunks", "cosine"),
                        "jdbc:postgresql://localhost:5432/actual_db", null, null, "hybrid", Map.of()));

        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                mock(DataSource.class), "hybrid", Map.of());
        assertThrows(BaseError.class, () -> store.ensureCollection("kb_chunks", "vector", 2001, Map.of()));
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
            if (sql.startsWith("SELECT EXISTS")) {
                return existsStatement;
            }
            return upsertStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(false);

        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                dataSource, "hybrid", Map.of("vector_field", "embedding"));

        store.add(
                List.of(Map.of("id", "1", "text", "a", "embedding", List.of(1.0f, 0.0f)),
                        Map.of("id", "2", "text", "b", "embedding", List.of(0.0f, 1.0f)),
                        Map.of("id", "3", "text", "c", "embedding", List.of(0.5f, 0.5f))),
                2, Map.of("index_type", "hnsw", "m", 8));

        verify(upsertStatement, times(2)).executeBatch();

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(ddl, times(5)).execute(ddlCaptor.capture());
        List<String> sqlStatements = ddlCaptor.getAllValues();
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("CREATE EXTENSION IF NOT EXISTS vector")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("CREATE INDEX IF NOT EXISTS")));
    }

    @Test
    void searchNormalizesDenseScoresAndCarriesMetadata() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement searchStatement = mock(PreparedStatement.class);
        ResultSet searchResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT EXISTS")) {
                return existsStatement;
            }
            return searchStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(true);
        when(searchStatement.executeQuery()).thenReturn(searchResult);
        when(searchResult.next()).thenReturn(true, false);
        when(searchResult.getString("chunk_id")).thenReturn("chunk-1");
        when(searchResult.getString("id")).thenReturn("row-1");
        when(searchResult.getString("doc_id")).thenReturn("doc-1");
        when(searchResult.getString("text")).thenReturn("hello world");
        when(searchResult.getObject("metadata")).thenReturn(Map.of("source", "unit"));
        when(searchResult.getDouble("raw_score")).thenReturn(0.2d);

        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                dataSource, "vector", Map.of());

        List<SearchResult> results = store.search(List.of(1.0f, 0.0f), 3, Map.of("doc_id", "doc-1"), Map.of());

        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertEquals(0.8d, results.get(0).getScore(), 1e-6);
        assertEquals(0.2d, ((Number) results.get(0).getMetadata().get("raw_score")).doubleValue(), 1e-6);
        assertEquals(0.8d, ((Number) results.get(0).getMetadata().get("raw_score_scaled")).doubleValue(), 1e-6);
        assertEquals("unit", results.get(0).getMetadata().get("source"));
    }

    @Test
    void deleteAndCountUseSqlBackedPaths() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        PreparedStatement countStatement = mock(PreparedStatement.class);
        ResultSet countResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT EXISTS")) {
                return existsStatement;
            }
            if (sql.startsWith("DELETE FROM")) {
                return deleteStatement;
            }
            return countStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true, true);
        when(existsResult.getBoolean(1)).thenReturn(true);
        when(deleteStatement.executeUpdate()).thenReturn(2);
        when(countStatement.executeQuery()).thenReturn(countResult);
        when(countResult.next()).thenReturn(true);
        when(countResult.getLong(1)).thenReturn(2L);

        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                dataSource, "hybrid", Map.of());

        assertTrue(store.delete(List.of("chunk-1", "chunk-2"), Map.of("doc_id", "doc-1"), Map.of()));
        assertEquals(2L, store.count("kb_chunks"));
        assertFalse(store.delete(List.of(), Map.of(), Map.of()));
    }

    @Test
    void checkVectorFieldValidatesExpectedSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        PreparedStatement columnsStatement = mock(PreparedStatement.class);
        ResultSet columnsResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT EXISTS")) {
                return existsStatement;
            }
            return columnsStatement;
        });
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(true);
        when(columnsStatement.executeQuery()).thenReturn(columnsResult);
        when(columnsResult.next()).thenReturn(true, true, true, true, true, true, false);
        when(columnsResult.getString("column_name")).thenReturn("id", "text", "vector", "metadata", "doc_id",
                "chunk_id");
        when(columnsResult.getString("column_type")).thenReturn("text", "text", "vector(2)", "jsonb", "text", "text");

        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                dataSource, "vector", Map.of());

        store.checkVectorField();

        verify(columnsStatement).executeQuery();
    }

    @Test
    void hybridSearchSupportsWeightedAndRrfFusion() {
        PGVectorStore store = new TestPGVectorStore(new VectorStoreConfig("pgvector", "test_db", "kb_chunks", "cosine"),
                mock(DataSource.class), "hybrid", Map.of()) {
            @Override
            public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters,
                    Map<String, Object> options) {
                return List.of(new SearchResult("chunk-1", "dense doc", 0.9, Map.of()),
                        new SearchResult("chunk-2", "other doc", 0.2, Map.of()));
            }

            @Override
            public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters,
                    Map<String, Object> options) {
                return List.of(new SearchResult("chunk-1", "dense doc", 0.7, Map.of()),
                        new SearchResult("chunk-3", "sparse doc", 0.6, Map.of()));
            }
        };

        WeightedRankConfig weighted = new WeightedRankConfig();
        weighted.setDenseName(0.0);
        weighted.setDenseContent(0.8);
        weighted.setSparseContent(0.2);

        List<SearchResult> weightedResults =
            store.hybridSearch("query", List.of(1.0f, 0.0f), 3, 0.5, null, Map.of("rank_config", weighted));
        assertEquals("chunk-1", weightedResults.get(0).getId());

        RRFRankConfig rrf = new RRFRankConfig();
        List<SearchResult> rrfResults =
            store.hybridSearch("query", List.of(1.0f, 0.0f), 3, 0.5, null, Map.of("rank_config", rrf));
        assertNotNull(rrfResults);
        assertFalse(rrfResults.isEmpty());
    }

    private static class TestPGVectorStore extends PGVectorStore {
        private final DataSource dataSource;
        private final String indexType;
        private final Map<String, Object> options;

        private TestPGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType,
                Map<String, Object> options) {
            super(config, dataSource, indexType, options);
            this.dataSource = dataSource;
            this.indexType = indexType;
            this.options = options;
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            return new TestPGVectorStore(
                    new VectorStoreConfig("pgvector", getDatabaseName(), collectionName, getDistanceMetric()),
                    dataSource, indexType, options);
        }

        @Override
        protected void registerVectorTypes(Connection connection) {
        }
    }
}
