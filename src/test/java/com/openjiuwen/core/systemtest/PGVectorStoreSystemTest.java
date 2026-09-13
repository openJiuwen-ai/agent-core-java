/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag("system-test")
class PGVectorStoreSystemTest {
    private static final String JDBC_URL_PROPERTY = "pgvector.test.jdbcUrl";
    private static final String USERNAME_PROPERTY = "pgvector.test.username";
    private static final String PASSWORD_PROPERTY = "pgvector.test.password";
    private static final String JDBC_URL_ENV = "PGVECTOR_TEST_JDBC_URL";
    private static final String USERNAME_ENV = "PGVECTOR_TEST_USERNAME";
    private static final String PASSWORD_ENV = "PGVECTOR_TEST_PASSWORD";
    private static final String VECTOR_TABLE_NAME = "kb_chunks_system_test";
    private static final String KNOWLEDGE_BASE_TABLE_NAME = "kb_pg_kb_chunks_system_test";

    @Test
    void pgvectorStoreSupportsDenseSparseHybridAndKnowledgeBaseRoundTrip() throws SQLException {
        try (TestDatabase database = openTestDatabase()) {
            PGVectorStore store = createStore(database, VECTOR_TABLE_NAME);
            verifyVectorStore(store);
            verifyPgVectorExtension(database);
            verifyKnowledgeBase(database);
        }
    }

    private static TestDatabase openTestDatabase() throws SQLException {
        Optional<String> jdbcUrl = readSetting(JDBC_URL_PROPERTY, JDBC_URL_ENV);
        Optional<String> username = readSetting(USERNAME_PROPERTY, USERNAME_ENV);
        Optional<String> password = readSetting(PASSWORD_PROPERTY, PASSWORD_ENV);
        boolean hasDatabaseConfig = jdbcUrl.isPresent() && username.isPresent() && password.isPresent();
        assumeTrue(hasDatabaseConfig,
                "Set PGVECTOR_TEST_JDBC_URL, PGVECTOR_TEST_USERNAME and PGVECTOR_TEST_PASSWORD to run this test");

        TestDatabase database = new TestDatabase(jdbcUrl.orElseThrow(), username.orElseThrow(),
                password.orElseThrow());
        database.resetTables();
        return database;
    }

    private static Optional<String> readSetting(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue);
        }
        return Optional.ofNullable(System.getenv(environmentName)).filter(value -> !value.isBlank());
    }

    private static PGVectorStore createStore(TestDatabase database, String tableName) {
        VectorStoreConfig config =
                new VectorStoreConfig("pgvector", database.databaseName, tableName, "cosine");
        return new PGVectorStore(config, database.jdbcUrl, database.username, database.password, "hybrid",
                Map.of("index_type", "hnsw", "m", 8));
    }

    private static void verifyVectorStore(PGVectorStore store) {
        store.add(List.of(
                Map.of("id", "chunk-1", "chunk_id", "chunk-1", "doc_id", "doc-1",
                        "text", "apple banana orange", "vector", List.of(1.0f, 0.0f),
                        "metadata", Map.of("source", "web")),
                Map.of("id", "chunk-2", "chunk_id", "chunk-2", "doc_id", "doc-2",
                        "text", "banana grape pear", "vector", List.of(0.0f, 1.0f),
                        "metadata", Map.of("source", "notes"))),
                2, Map.of("index_type", "hnsw"));

        assertTrue(store.tableExists(VECTOR_TABLE_NAME));
        assertEquals(2L, store.count(VECTOR_TABLE_NAME));
        assertEquals(1, store.queryByFilters(Map.of("doc_id", "doc-1"), 5).size());
        assertEquals("chunk-1", store.search(List.of(1.0f, 0.0f), 1, null, Map.of()).get(0).getId());
        assertFalse(store.sparseSearch("banana", 2, null, Map.of()).isEmpty());
        assertFalse(store.hybridSearch("apple", List.of(1.0f, 0.0f), 2, 0.7, null, Map.of()).isEmpty());
        assertTrue(store.delete(List.of("chunk-2"), null, Map.of()));
        assertEquals(1L, store.count(VECTOR_TABLE_NAME));
        store.checkVectorField();
    }

    private static void verifyPgVectorExtension(TestDatabase database) throws SQLException {
        try (Connection connection = database.openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'vector'")) {
            assertTrue(resultSet.next());
        }
    }

    private static void verifyKnowledgeBase(TestDatabase database) {
        PGVectorStore store = createStore(database, KNOWLEDGE_BASE_TABLE_NAME);
        SimpleKnowledgeBase knowledgeBase =
                new SimpleKnowledgeBase(new KnowledgeBaseConfig("pg_kb", "hybrid", false, 64, 8), store,
                        new FixedEmbedding(), null, new CharChunker(64, 8), null, null, null);

        List<String> documentIds = knowledgeBase.addDocuments(
                List.of(new Document("doc-10", "apple knowledge base document", Map.of("source", "system-test"))));
        assertEquals(List.of("doc-10"), documentIds);

        List<RetrievalResult> results = knowledgeBase.retrieve("apple", new RetrievalConfig());
        assertFalse(results.isEmpty());
        assertTrue(knowledgeBase.deleteDocuments(List.of("doc-10")));
    }

    private static final class TestDatabase implements AutoCloseable {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String databaseName;

        private TestDatabase(String jdbcUrl, String username, String password) throws SQLException {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.databaseName = readDatabaseName();
        }

        @Override
        public void close() throws SQLException {
            resetTables();
        }

        private String readDatabaseName() throws SQLException {
            try (Connection connection = openConnection()) {
                String catalog = connection.getCatalog();
                if (catalog == null || catalog.isBlank()) {
                    throw new SQLException("Unable to determine the PGVector test database name");
                }
                return catalog;
            }
        }

        private Connection openConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        private void resetTables() throws SQLException {
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS kb_chunks_system_test");
                statement.executeUpdate("DROP TABLE IF EXISTS kb_pg_kb_chunks_system_test");
            }
        }
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            if (text.contains("apple")) {
                return List.of(1.0f, 0.0f);
            }
            return List.of(0.0f, 1.0f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize) {
            return texts.stream().map(text -> embedQuery(String.valueOf(text))).toList();
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
