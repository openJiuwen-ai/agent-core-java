/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
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
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
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
                    container.getJdbcUrl());

            store.add(List.of(
                    Map.of(
                            "id", "chunk-1",
                            "chunk_id", "chunk-1",
                            "doc_id", "doc-1",
                            "text", "apple banana orange",
                            "vector", List.of(1.0, 0.0),
                            "metadata", Map.of("source", "web")),
                    Map.of(
                            "id", "chunk-2",
                            "chunk_id", "chunk-2",
                            "doc_id", "doc-2",
                            "text", "banana grape pear",
                            "vector", List.of(0.0, 1.0),
                            "metadata", Map.of("source", "notes"))
            ), 2, Map.of("index_type", "hnsw")).join();

            assertTrue(store.tableExists("kb_chunks").join());
            assertEquals("chunk-1", store.search(List.of(1.0, 0.0), 1, VectorStore.VectorStoreFilter.none(), Map.of()).join().get(0).getChunkId());
            assertFalse(store.sparseSearch("banana", 2, VectorStore.VectorStoreFilter.none(), Map.of()).join().isEmpty());
            assertFalse(store.hybridSearch("apple", List.of(1.0, 0.0), 2, 0.7, VectorStore.VectorStoreFilter.none(), Map.of()).join().isEmpty());
            assertTrue(store.delete(List.of("chunk-2"), VectorStore.DeleteFilter.none(), Map.of()).join());
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
                    container.getJdbcUrl());

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

    private static final class FixedEmbedding extends Embedding {
        @Override
        public java.util.concurrent.CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    text.contains("apple") ? List.of(1.0, 0.0) : List.of(0.0, 1.0));
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts, Integer batchSize, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    texts.stream().map(text -> text.contains("apple") ? List.of(1.0, 0.0) : List.of(0.0, 1.0)).toList());
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
