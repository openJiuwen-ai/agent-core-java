/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.systemtest;

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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class PGVectorStoreSystemTest {

    @Test
    void pgvectorStoreSupportsDenseSparseHybridAndKnowledgeBaseRoundTrip() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for PGVector system test");

        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
                .withDatabaseName("pgvector_test")
                .withUsername("test")
                .withPassword("test")) {
            container.start();

            PGVectorStore store = new PGVectorStore(
                    new VectorStoreConfig("pgvector", "pgvector_test", "kb_chunks", "cosine"),
                    container.getJdbcUrl(),
                    container.getUsername(),
                    container.getPassword(),
                    "hybrid",
                    Map.of("index_type", "hnsw", "m", 8));

            store.add(List.of(
                    Map.of(
                            "id", "chunk-1",
                            "chunk_id", "chunk-1",
                            "doc_id", "doc-1",
                            "text", "apple banana orange",
                            "vector", List.of(1.0f, 0.0f),
                            "metadata", Map.of("source", "web")),
                    Map.of(
                            "id", "chunk-2",
                            "chunk_id", "chunk-2",
                            "doc_id", "doc-2",
                            "text", "banana grape pear",
                            "vector", List.of(0.0f, 1.0f),
                            "metadata", Map.of("source", "notes"))
            ), 2, Map.of("index_type", "hnsw"));

            assertTrue(store.tableExists("kb_chunks"));
            assertEquals(2L, store.count("kb_chunks"));
            assertEquals(1, store.queryByFilters(Map.of("doc_id", "doc-1"), 5).size());
            assertEquals("chunk-1", store.search(List.of(1.0f, 0.0f), 1, null, Map.of()).get(0).getId());
            assertFalse(store.sparseSearch("banana", 2, null, Map.of()).isEmpty());
            assertFalse(store.hybridSearch("apple", List.of(1.0f, 0.0f), 2, 0.7, null, Map.of()).isEmpty());
            assertTrue(store.delete(List.of("chunk-2"), null, Map.of()));
            assertEquals(1L, store.count("kb_chunks"));
            store.checkVectorField();

            try (Connection connection = DriverManager.getConnection(
                    container.getJdbcUrl(),
                    container.getUsername(),
                    container.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT extname FROM pg_extension WHERE extname = 'vector'")) {
                assertTrue(resultSet.next());
            }

            PGVectorStore kbStore = new PGVectorStore(
                    new VectorStoreConfig("pgvector", "pgvector_test", "kb_pg_kb_chunks", "cosine"),
                    container.getJdbcUrl(),
                    container.getUsername(),
                    container.getPassword(),
                    "hybrid");

            SimpleKnowledgeBase knowledgeBase = new SimpleKnowledgeBase(
                    new KnowledgeBaseConfig("pg_kb", "hybrid", false, 64, 8),
                    kbStore,
                    new FixedEmbedding(),
                    null,
                    new CharChunker(64, 8),
                    null,
                    null,
                    null);

            List<String> docIds = knowledgeBase.addDocuments(List.of(
                    new Document("doc-10", "apple knowledge base document", Map.of("source", "system-test"))));
            assertEquals(List.of("doc-10"), docIds);

            List<RetrievalResult> results = knowledgeBase.retrieve("apple", new RetrievalConfig());
            assertFalse(results.isEmpty());
            assertTrue(knowledgeBase.deleteDocuments(List.of("doc-10")));
        }
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return text.contains("apple") ? List.of(1.0f, 0.0f) : List.of(0.0f, 1.0f);
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
}
