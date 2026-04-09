/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.InMemoryIndexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PGVectorKnowledgeBaseTest {

    @Test
    void simpleKnowledgeBaseUsesInMemoryIndexerAgainstPgVectorStore() throws Exception {
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

        PGVectorStore store = new TestPGVectorStore(
                new VectorStoreConfig("pgvector", "kb_db", "kb_pg_kb_chunks", "cosine"),
                dataSource,
                "vector",
                Map.of());

        SimpleKnowledgeBase knowledgeBase = new SimpleKnowledgeBase(
                new KnowledgeBaseConfig("pg_kb", "vector", false, 64, 8),
                store,
                new FixedEmbedding(),
                null,
                new CharChunker(64, 8),
                null,
                null,
                null);

        List<String> docIds = knowledgeBase.addDocuments(List.of(
                new Document("doc-1", "hello world from pgvector", Map.of("source", "test"))));

        assertEquals(List.of("doc-1"), docIds);
        assertInstanceOf(InMemoryIndexer.class, knowledgeBase.getIndexManager());
        verify(upsertStatement).executeBatch();
        verify(ddl).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS"));
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(this::embedQuery).toList();
        }

        @Override
        public int getDimension() {
            return 2;
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
