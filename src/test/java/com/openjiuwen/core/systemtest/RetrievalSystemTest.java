/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.indexer.InMemoryIndexer;
import com.openjiuwen.core.retrieval.reranker.LexicalReranker;
import com.openjiuwen.core.retrieval.reranker.Reranker;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Retrieval module.
 * Tests embeddings, knowledge base, document indexing, retrieval, and reranking.
 * Corresponds to Python's retrieval showcase examples.
 */
@Tag("system-test")
class RetrievalSystemTest {

    @Nested
    @DisplayName("HashEmbedding Tests")
    class HashEmbeddingTests {

        @Test
        @DisplayName("HashEmbedding generates consistent vectors")
        void testHashEmbeddingConsistency() {
            HashEmbedding embedding = new HashEmbedding(32, 256);

            List<Float> vec1 = embedding.embedQuery("hello world");
            List<Float> vec2 = embedding.embedQuery("hello world");

            assertNotNull(vec1);
            assertNotNull(vec2);
            assertEquals(32, vec1.size(), "Dimension should be 32");
            assertEquals(vec1, vec2, "Same text should produce same embedding");
        }

        @Test
        @DisplayName("HashEmbedding different texts produce different vectors")
        void testHashEmbeddingDifferentTexts() {
            HashEmbedding embedding = new HashEmbedding();

            List<Float> vec1 = embedding.embedQuery("hello");
            List<Float> vec2 = embedding.embedQuery("world");

            assertNotNull(vec1);
            assertNotNull(vec2);
            assertFalse(vec1.equals(vec2),
                    "Different texts should (likely) produce different embeddings");
        }

        @Test
        @DisplayName("HashEmbedding batch embedDocuments")
        void testHashEmbeddingBatch() {
            HashEmbedding embedding = new HashEmbedding(64, 256);

            List<List<Float>> vectors = embedding.embedDocuments(
                    List.of("text1", "text2", "text3"), null);

            assertNotNull(vectors);
            assertEquals(3, vectors.size());
            for (List<Float> vec : vectors) {
                assertEquals(64, vec.size());
            }
        }
    }

    @Nested
    @DisplayName("API Embedding Tests (Remote)")
    class APIEmbeddingTests {

        @Test
        @DisplayName("APIEmbedding embeds single query via remote API")
        void testApiEmbeddingSingleQuery() {
            EmbeddingConfig config = new EmbeddingConfig(
                    ApiConfigLoader.getEmbeddingModelName(),
                    ApiConfigLoader.getEmbeddingApiBase(),
                    ApiConfigLoader.getApiKey());

            APIEmbedding embedding = new APIEmbedding(config, 30, 2, null, 256, 1);

            List<Float> vector = embedding.embedQuery("你好世界");
            assertNotNull(vector, "Embedding vector should not be null");
            assertFalse(vector.isEmpty(), "Embedding vector should not be empty");
            System.out.println("[APIEmbedding Single] Dimension: " + vector.size()
                    + ", First values: " + vector.subList(0, Math.min(5, vector.size())));
        }

        @Test
        @DisplayName("APIEmbedding batch embed multiple documents")
        void testApiEmbeddingBatch() {
            EmbeddingConfig config = new EmbeddingConfig(
                    ApiConfigLoader.getEmbeddingModelName(),
                    ApiConfigLoader.getEmbeddingApiBase(),
                    ApiConfigLoader.getApiKey());

            APIEmbedding embedding = new APIEmbedding(config, 30, 2, null, 256, 1);

            List<String> texts = List.of(
                    "OpenJiuWen是一个智能体框架",
                    "大语言模型驱动了现代AI应用",
                    "检索增强生成技术提升了回答准确性");

            List<List<Float>> vectors = embedding.embedDocuments(texts, null);
            assertNotNull(vectors);
            assertEquals(3, vectors.size(), "Should have 3 embeddings");
            for (List<Float> vec : vectors) {
                assertFalse(vec.isEmpty(), "Each embedding should be non-empty");
            }
            System.out.println("[APIEmbedding Batch] Count: " + vectors.size()
                    + ", Dimension: " + vectors.get(0).size());
        }
    }

    @Nested
    @DisplayName("SimpleKnowledgeBase Tests (InMemory)")
    class KnowledgeBaseTests {

        @Test
        @DisplayName("KnowledgeBase add documents and retrieve with HashEmbedding")
        void testKnowledgeBaseAddAndRetrieve() {
            KnowledgeBaseConfig kbConfig = new KnowledgeBaseConfig("test_kb_1");
            HashEmbedding embedding = new HashEmbedding(32, 256);
            InMemoryVectorStore vectorStore = new InMemoryVectorStore("test_collection");
            InMemoryIndexer indexer = new InMemoryIndexer(vectorStore);

            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    kbConfig, vectorStore, embedding, null, new CharChunker(512, 64), indexer, null, null);

            List<Document> docs = List.of(
                    new Document("doc1", "OpenJiuWen是一个开源的智能体开发框架，支持多种AI模型的集成。"),
                    new Document("doc2", "Java编程语言是目前最流行的企业级开发语言之一。"),
                    new Document("doc3", "RAG技术通过检索相关文档来增强大模型的回答准确性。"));

            List<String> docIds = kb.addDocuments(docs);
            assertNotNull(docIds);
            assertFalse(docIds.isEmpty(), "Should return document IDs");
            System.out.println("[KB Add] Document IDs: " + docIds);

            RetrievalConfig retrievalConfig = new RetrievalConfig();
            retrievalConfig.setTopK(3);

            List<RetrievalResult> results = kb.retrieve("什么是OpenJiuWen?", retrievalConfig);
            assertNotNull(results);
            System.out.println("[KB Retrieve] Results count: " + results.size());
            for (RetrievalResult r : results) {
                System.out.println("  - Score: " + r.getScore() + ", Text: "
                        + r.getText().substring(0, Math.min(50, r.getText().length())) + "...");
            }
        }

        @Test
        @DisplayName("KnowledgeBase statistics")
        void testKnowledgeBaseStatistics() {
            KnowledgeBaseConfig kbConfig = new KnowledgeBaseConfig("stats_kb");
            HashEmbedding embedding = new HashEmbedding();
            InMemoryVectorStore vectorStore = new InMemoryVectorStore("stats_collection");
            InMemoryIndexer indexer = new InMemoryIndexer(vectorStore);

            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    kbConfig, vectorStore, embedding, null, new CharChunker(512, 64), indexer, null, null);

            Map<String, Object> stats = kb.getStatistics();
            assertNotNull(stats);
            System.out.println("[KB Stats] " + stats);
        }

        @Test
        @DisplayName("KnowledgeBase delete documents")
        void testKnowledgeBaseDeleteDocuments() {
            KnowledgeBaseConfig kbConfig = new KnowledgeBaseConfig("delete_kb");
            HashEmbedding embedding = new HashEmbedding();
            InMemoryVectorStore vectorStore = new InMemoryVectorStore("delete_collection");
            InMemoryIndexer indexer = new InMemoryIndexer(vectorStore);

            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    kbConfig, vectorStore, embedding, null, new CharChunker(512, 64), indexer, null, null);

            List<Document> docs = List.of(
                    new Document("del_doc1", "测试文档一"),
                    new Document("del_doc2", "测试文档二"));

            List<String> ids = kb.addDocuments(docs);
            assertNotNull(ids);

            boolean deleted = kb.deleteDocuments(List.of("del_doc1"));
            System.out.println("[KB Delete] Deleted: " + deleted);
        }
    }

    @Nested
    @DisplayName("LexicalReranker Tests")
    class RerankerTests {

        @Test
        @DisplayName("LexicalReranker reranks by token overlap")
        void testLexicalReranker() {
            LexicalReranker reranker = new LexicalReranker();

            List<RetrievalResult> candidates = List.of(
                    new RetrievalResult("Java是一种编程语言", 0.5),
                    new RetrievalResult("Python也是一种流行的编程语言", 0.4),
                    new RetrievalResult("OpenJiuWen智能体框架支持Java和Python", 0.3));

            List<RetrievalResult> reranked = reranker.rerank(
                    "Java编程语言", candidates, 2);

            assertNotNull(reranked);
            assertEquals(2, reranked.size(), "Should return topK=2 results");
            System.out.println("[Reranker] Results:");
            for (RetrievalResult r : reranked) {
                System.out.println("  - Score: " + r.getScore()
                        + ", Text: " + r.getText());
            }
        }
    }

    @Nested
    @DisplayName("Multi-KB Retrieval Tests")
    class MultiKBTests {

        @Test
        @DisplayName("Retrieve across multiple knowledge bases")
        void testMultiKBRetrieval() {
            HashEmbedding embedding = new HashEmbedding();

            InMemoryVectorStore vs1 = new InMemoryVectorStore("coll1");
            SimpleKnowledgeBase kb1 = new SimpleKnowledgeBase(
                    new KnowledgeBaseConfig("multi_kb_1"),
                    vs1,
                    embedding, null, new CharChunker(512, 64), new InMemoryIndexer(vs1), null, null);
            kb1.addDocuments(List.of(
                    new Document("d1", "OpenJiuWen支持智能体开发"),
                    new Document("d2", "框架提供了丰富的工具集成")));

            InMemoryVectorStore vs2 = new InMemoryVectorStore("coll2");
            SimpleKnowledgeBase kb2 = new SimpleKnowledgeBase(
                    new KnowledgeBaseConfig("multi_kb_2"),
                    vs2,
                    embedding, null, new CharChunker(512, 64), new InMemoryIndexer(vs2), null, null);
            kb2.addDocuments(List.of(
                    new Document("d3", "Java版本的智能体核心库"),
                    new Document("d4", "检索增强生成模块")));

            List<RetrievalResult> results = SimpleKnowledgeBase.retrieveMultiKb(
                    List.of(kb1, kb2), "智能体", 3);

            assertNotNull(results);
            System.out.println("[MultiKB] Results: " + results.size());
            for (RetrievalResult r : results) {
                System.out.println("  - " + r.getText());
            }
        }
    }

    @Nested
    @DisplayName("Document Model Tests")
    class DocumentTests {

        @Test
        @DisplayName("Document creation with auto-generated ID")
        void testDocumentAutoId() {
            Document doc = new Document("Some text content");
            assertNotNull(doc.getId(), "ID should be auto-generated");
            assertEquals("Some text content", doc.getText());
            assertNotNull(doc.getMetadata());
        }

        @Test
        @DisplayName("Document creation with metadata")
        void testDocumentMetadata() {
            Document doc = new Document("doc_meta", "Text with metadata",
                    Map.of("source", "test", "category", "integration"));
            assertEquals("doc_meta", doc.getId());
            assertEquals("test", doc.getMetadata().get("source"));
            assertEquals("integration", doc.getMetadata().get("category"));
        }
    }
}
