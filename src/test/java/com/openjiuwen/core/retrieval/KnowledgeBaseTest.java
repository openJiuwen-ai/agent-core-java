/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.indexing.indexer.InMemoryIndexer;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.SimpleTripleExtractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Knowledge base regression tests ported from Python retrieval unit tests.
 */
class KnowledgeBaseTest {

    @Test
    @DisplayName("knowledge base config validation and close")
    void testKnowledgeBaseValidationAndClose() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexer = mock(Indexer.class);
        stubCompatibleConfig(vectorStore, indexer);

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config);
        kb.setIndexManager(indexer);
        kb.setVectorStore(vectorStore);
        assertEquals(vectorStore, kb.getVectorStore());
        assertEquals(indexer, kb.getIndexManager());

        VectorStore badVectorStore = mock(VectorStore.class);
        Indexer badIndexer = mock(Indexer.class);
        stubCompatibleConfig(badVectorStore, badIndexer);
        when(badVectorStore.getDatabaseName()).thenReturn("db1");
        when(badIndexer.getDatabaseName()).thenReturn("db2");
        assertThrows(BaseError.class, () -> new ConcreteKnowledgeBase(config, badVectorStore, badIndexer));

        kb.close();
    }

    @Test
    @DisplayName("simple knowledge base end to end")
    void testSimpleKnowledgeBase() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb", "vector", false, 64, 8);
        InMemoryVectorStore store = new InMemoryVectorStore("kb_test_kb_chunks");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config,
                store,
                new HashEmbedding(),
                new FakeParser(),
                new CharChunker(64, 8),
                new InMemoryIndexer(store),
                null,
                null);

        List<Document> parsed = kb.parseFiles(List.of("a.txt", "b.txt"));
        assertEquals(4, parsed.size());

        List<String> docIds = kb.addDocuments(List.of(
                new Document("doc_1", "Alice knows Bob. Bob likes Carol.", Map.of("source", "test")),
                new Document("doc_2", "Carol visits Paris.", Map.of())));
        assertEquals(2, docIds.size());

        List<RetrievalResult> results = kb.retrieve("Alice", new RetrievalConfig());
        assertFalse(results.isEmpty());

        assertTrue(kb.deleteDocuments(List.of("doc_2")));
        assertEquals(1, kb.updateDocuments(List.of(new Document("doc_1", "Alice knows Carol.", Map.of()))).size());
        assertEquals("test_kb", kb.getStatistics().get("kb_id"));

        assertThrows(BaseError.class, () -> new SimpleKnowledgeBase(config).retrieve("test", new RetrievalConfig()));
        assertThrows(BaseError.class, () -> new SimpleKnowledgeBase(config).addDocuments(List.of(new Document("x"))));
    }

    @Test
    @DisplayName("knowledge base parse urls respects parser support and assigns ids")
    void testKnowledgeBaseParseUrlsRespectsSupport() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("url_kb");
        UrlAwareParser parser = new UrlAwareParser();
        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config);
        kb.setParser(parser);

        List<Document> docs = kb.parseUrls(List.of(
                "https://example.com/a",
                "ftp://example.com/b",
                "https://example.com/c"));

        assertEquals(2, docs.size());
        assertEquals(List.of("https://example.com/a", "https://example.com/c"), parser.parsedDocs);
        assertNotEquals(docs.get(0).getId(), docs.get(1).getId());
    }

    @Test
    @DisplayName("simple knowledge base retrieve with score threshold and empty result")
    void testSimpleKnowledgeBaseScoreThresholdAndEmpty() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("threshold_kb", "vector", false, 64, 8);
        InMemoryVectorStore store = new InMemoryVectorStore("kb_threshold_kb_chunks");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config,
                store,
                new HashEmbedding(),
                new FakeParser(),
                new CharChunker(64, 8),
                new InMemoryIndexer(store),
                null,
                null);
        kb.addDocuments(List.of(new Document("doc_1", "Alice knows Bob.", Map.of())));

        RetrievalConfig highThreshold = new RetrievalConfig();
        highThreshold.setScoreThreshold(0.99);
        List<RetrievalResult> filtered = kb.retrieve("Alice", highThreshold);
        // With a very high threshold, most results will be filtered out
        assertTrue(filtered.size() <= 1);

        // Empty results for a completely unrelated query
        List<RetrievalResult> emptyish = kb.retrieve("zzzzzzzzz_nonexistent_query_zzzzzzzzz", new RetrievalConfig());
        assertNotNull(emptyish);
    }

    @Test
    @DisplayName("knowledge base config validation rejects invalid index type")
    void testKnowledgeBaseConfigValidation() {
        assertThrows(BaseError.class, () -> new KnowledgeBaseConfig("test", "invalid_type", false, 64, 8));
        assertThrows(BaseError.class, () -> {
            KnowledgeBaseConfig invalid = new KnowledgeBaseConfig();
            invalid.validate();
        });
    }

    @Test
    @DisplayName("simple knowledge base with provided retriever and multi kb")
    void testSimpleKnowledgeBaseWithRetrieverAndMultiKb() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
        Retriever retriever = mock(Retriever.class);
        when(retriever.retrieve(eq("test query"), eq(5), eq(null), eq("hybrid"), any()))
                .thenReturn(List.of(new RetrievalResult("Test result", 0.95)));
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config, null, null, null, null, null, null, retriever);

        List<RetrievalResult> results = kb.retrieve("test query", new RetrievalConfig());
        assertEquals(1, results.size());

        KnowledgeBase kb1 = mock(KnowledgeBase.class);
        when(kb1.getConfig()).thenReturn(new KnowledgeBaseConfig("kb1"));
        when(kb1.retrieve(eq("q"), any())).thenReturn(List.of(
                new RetrievalResult("Result 1", 0.9, Map.of("raw_score", 0.9, "raw_score_scaled", 0.9), null, null),
                new RetrievalResult("Result 2", 0.8, Map.of(), null, null)));
        KnowledgeBase kb2 = mock(KnowledgeBase.class);
        when(kb2.getConfig()).thenReturn(new KnowledgeBaseConfig("kb2"));
        when(kb2.retrieve(eq("q"), any())).thenReturn(List.of(
                new RetrievalResult("Result 2", 0.95, Map.of("raw_score", 0.95, "raw_score_scaled", 0.95), null, null)));

        List<String> multiResults = SimpleKnowledgeBase.retrieveMultiKb(List.of(kb1, kb2), "q", 5);
        assertEquals(2, multiResults.size());
        assertEquals("Result 2", multiResults.get(0));

        List<?> withSource = SimpleKnowledgeBase.retrieveMultiKbWithSource(List.of(kb1, kb2), "q", 5);
        assertEquals(2, withSource.size());
        assertTrue(withSource.get(0) instanceof com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult);
    }

    @Test
    @DisplayName("graph knowledge base end to end and close")
    void testGraphKnowledgeBase() throws Exception {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig("graph_kb", "vector", true, 64, 8);
        InMemoryVectorStore store = new InMemoryVectorStore("kb_graph_kb_chunks");
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                config,
                store,
                new HashEmbedding(),
                new FakeParser(),
                new CharChunker(64, 8),
                new SimpleTripleExtractor(),
                new InMemoryIndexer(store),
                null,
                null,
                null);

        List<String> ids = kb.addDocuments(List.of(new Document("doc_1", "Alice knows Bob. Bob likes Carol.", Map.of())));
        assertEquals(1, ids.size());
        assertNotNull(kb.getStatistics().get("triple_index_info"));

        List<RetrievalResult> results = kb.retrieve("Alice", new RetrievalConfig());
        assertFalse(results.isEmpty());

        KnowledgeBaseConfig noGraphConfig = new KnowledgeBaseConfig("simple_like", "vector", false, 64, 8);
        GraphKnowledgeBase noGraphKb = new GraphKnowledgeBase(
                noGraphConfig,
                store,
                new HashEmbedding(),
                new FakeParser(),
                new CharChunker(64, 8),
                new SimpleTripleExtractor(),
                new InMemoryIndexer(store),
                null,
                null,
                null);
        noGraphKb.addDocuments(List.of(new Document("doc_2", "Carol visits Paris.", Map.of())));
        assertFalse(noGraphKb.retrieve("Carol", new RetrievalConfig()).isEmpty());

        assertTrue(kb.deleteDocuments(List.of("doc_1")));
        assertEquals(1, kb.updateDocuments(List.of(new Document("doc_1", "Alice supports Dave.", Map.of()))).size());

        Retriever chunkRetriever = mock(Retriever.class);
        Retriever tripleRetriever = mock(Retriever.class);
        GraphKnowledgeBase closeKb = new GraphKnowledgeBase(config, null, null, null, null, null, null, null, chunkRetriever, tripleRetriever);
        GraphRetriever graphRetriever = mock(GraphRetriever.class);
        Field field = GraphKnowledgeBase.class.getDeclaredField("graphRetriever");
        field.setAccessible(true);
        field.set(closeKb, graphRetriever);
        closeKb.close();
    }

    private static void stubCompatibleConfig(VectorStore vectorStore, Indexer indexer) {
        when(vectorStore.getDatabaseName()).thenReturn("db");
        when(indexer.getDatabaseName()).thenReturn("db");
        when(vectorStore.getDistanceMetric()).thenReturn("cosine");
        when(indexer.getDistanceMetric()).thenReturn("cosine");
        when(vectorStore.getTextField()).thenReturn("text");
        when(indexer.getTextField()).thenReturn("text");
        when(vectorStore.getVectorField()).thenReturn("vector");
        when(indexer.getVectorField()).thenReturn("vector");
        when(vectorStore.getSparseVectorField()).thenReturn("sparse_vector");
        when(indexer.getSparseVectorField()).thenReturn("sparse_vector");
        when(vectorStore.getMetadataField()).thenReturn("metadata");
        when(indexer.getMetadataField()).thenReturn("metadata");
        when(vectorStore.getDocIdField()).thenReturn("doc_id");
        when(indexer.getDocIdField()).thenReturn("doc_id");
    }

    private static final class ConcreteKnowledgeBase extends KnowledgeBase {

        private ConcreteKnowledgeBase(KnowledgeBaseConfig config) {
            super(config);
        }

        private ConcreteKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Indexer indexer) {
            super(config, vectorStore, null, null, null, null, indexer, null, null);
        }

        @Override
        public List<String> addDocuments(List<Document> documents) {
            return documents.stream().map(Document::getId).toList();
        }

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalConfig config) {
            return List.of();
        }

        @Override
        public boolean deleteDocuments(List<String> docIds) {
            return true;
        }

        @Override
        public List<String> updateDocuments(List<Document> documents) {
            return documents.stream().map(Document::getId).toList();
        }

        @Override
        public Map<String, Object> getStatistics() {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("kb_id", getConfig().getKbId());
            return stats;
        }
    }

    private static final class FakeParser extends Parser {
        @Override
        protected String parseContent(String doc, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            return "content of " + doc;
        }

        @Override
        public List<Document> parse(String doc, String docId, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            return List.of(
                    new Document(doc + "_1", "content 1 from " + doc, Map.of()),
                    new Document(doc + "_2", "content 2 from " + doc, Map.of()));
        }
    }

    private static final class UrlAwareParser extends Parser {

        private final List<String> parsedDocs = new ArrayList<>();

        @Override
        public boolean supports(String doc) {
            return doc != null && doc.startsWith("http");
        }

        @Override
        public List<Document> parse(String doc, String docId, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            parsedDocs.add(doc);
            return List.of(new Document(docId, "parsed " + doc, Map.of()));
        }

        @Override
        protected String parseContent(String doc, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
