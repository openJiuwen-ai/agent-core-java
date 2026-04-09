/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticStorePGVectorTest {

    @Test
    void createCollectionUsesExplicitBootstrapWithoutInsert() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddl = mock(Statement.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(ddl);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(existsResult);
        when(existsResult.next()).thenReturn(true);
        when(existsResult.getBoolean(1)).thenReturn(false);

        SemanticStore semanticStore = new SemanticStore(new TestPGVectorStore(
                new VectorStoreConfig("pgvector", "memory_db", "memory_fragments", "cosine"),
                dataSource,
                "vector",
                Map.of()));

        semanticStore.createCollection("memory_fragments", 3, Map.of("index_type", "hnsw"));

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(ddl, org.mockito.Mockito.atLeastOnce()).execute(ddlCaptor.capture());
        assertTrue(ddlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS")));
        verify(connection, never()).prepareStatement(org.mockito.ArgumentMatchers.startsWith("INSERT INTO"));
    }

    @Test
    void addDocsBootstrapsAndWritesRows() throws Exception {
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

        SemanticStore semanticStore = new SemanticStore(new TestPGVectorStore(
                new VectorStoreConfig("pgvector", "memory_db", "memory_fragments", "cosine"),
                dataSource,
                "vector",
                Map.of()), new FixedEmbedding());

        boolean stored = semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")), "memory_fragments");

        assertTrue(stored);
        verify(upsertStatement).executeBatch();
        verify(ddl).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS"));
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f, 0.5f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(this::embedQuery).toList();
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static class TestPGVectorStore extends PGVectorStore {
        private final DataSource dataSource;
        private final String indexType;
        private final Map<String, Object> options;

        private TestPGVectorStore(VectorStoreConfig config,
                                  DataSource dataSource,
                                  String indexType,
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
                    dataSource,
                    indexType,
                    options);
        }

        @Override
        protected void registerVectorTypes(Connection connection) {
        }
    }
}
