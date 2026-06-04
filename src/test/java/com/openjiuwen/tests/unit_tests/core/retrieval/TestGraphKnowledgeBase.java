/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphRAG knowledge base implementation test cases.
 *
 * <p>Mirrors Python's {@code test_graph_knowledge_base.py} in
 * {@code tests/unit_tests/core/retrieval/test_graph_knowledge_base.py}.</p>
 */
@DisplayName("GraphKnowledgeBase Tests")
class TestGraphKnowledgeBase {

    @Test
    @DisplayName("test_parse_files_success")
    void testParseFilesSuccess() {
        FakeParser parser = new FakeParser();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null, parser,
                null, null, null, null, null, null);

        List<Document> documents = kb.parseFiles(List.of("test1.txt"));

        assertThat(documents).hasSize(1);
        assertThat(parser.parseCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_add_documents_with_graph")
    void testAddDocumentsWithGraph() {
        FakeChunker chunker = new FakeChunker();
        FakeExtractor extractor = new FakeExtractor();
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, new FakeEmbedding(),
                null, chunker, extractor, indexer, null, null, null);

        List<String> docIds = kb.addDocuments(List.of(new Document("doc_1", "Test document")));

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.buildCalls).isEqualTo(2);
        assertThat(indexer.buildIndexNames).containsExactly("kb_test_kb_chunks", "kb_test_kb_triples");
        assertThat(extractor.extractCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_add_documents_without_graph")
    void testAddDocumentsWithoutGraph() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(false), null, new FakeEmbedding(),
                null, new FakeChunker(), null, indexer, null, null, null);

        List<String> docIds = kb.addDocuments(List.of(new Document("doc_1", "Test document")));

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.buildCalls).isEqualTo(1);
        assertThat(indexer.buildIndexNames).containsExactly("kb_test_kb_chunks");
    }

    @Test
    @DisplayName("test_retrieve_with_graph")
    void testRetrieveWithGraph() {
        RecordingRetriever chunkRetriever = new RecordingRetriever(List.of(new RetrievalResult("Test result", 0.95)));
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null,
                null, null, null, null, null, chunkRetriever, new RecordingRetriever(List.of()));
        RetrievalConfig retrievalConfig = new RetrievalConfig();
        retrievalConfig.setUseGraph(true);
        retrievalConfig.setTopK(5);

        List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Test result");
        assertThat(chunkRetriever.retrieveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_retrieve_with_agentic")
    void testRetrieveWithAgentic() {
        RecordingRetriever chunkRetriever = new RecordingRetriever(List.of(new RetrievalResult("Agentic result", 0.98)));
        FakeModelClient llmClient = new FakeModelClient(
                "[]",
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null,
                null, null, null, null, llmClient, chunkRetriever, new RecordingRetriever(List.of()));
        RetrievalConfig retrievalConfig = new RetrievalConfig();
        retrievalConfig.setUseGraph(true);
        retrievalConfig.setAgentic(true);
        retrievalConfig.setTopK(5);

        List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Agentic result");
        assertThat(llmClient.invokeCalls).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("test_retrieve_without_graph_falls_back_to_simple")
    void testRetrieveWithoutGraphFallsBackToSimple() {
        RecordingRetriever simpleRetriever = new RecordingRetriever(List.of(new RetrievalResult("Simple result", 0.9)));
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(false), null, null,
                null, null, null, null, new FakeModelClient(), simpleRetriever, null);
        RetrievalConfig retrievalConfig = new RetrievalConfig();
        retrievalConfig.setUseGraph(false);
        retrievalConfig.setTopK(5);

        List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Simple result");
        assertThat(simpleRetriever.retrieveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_delete_documents_with_graph")
    void testDeleteDocumentsWithGraph() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null,
                null, null, null, indexer, null, null, null);

        boolean result = kb.deleteDocuments(List.of("doc_1"));

        assertThat(result).isTrue();
        assertThat(indexer.deleteCalls).isEqualTo(2);
        assertThat(indexer.deleteIndexNames).containsExactly("kb_test_kb_chunks", "kb_test_kb_triples");
    }

    @Test
    @DisplayName("test_delete_documents_without_graph")
    void testDeleteDocumentsWithoutGraph() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(false), null, null,
                null, null, null, indexer, null, null, null);

        boolean result = kb.deleteDocuments(List.of("doc_1"));

        assertThat(result).isTrue();
        assertThat(indexer.deleteCalls).isEqualTo(1);
        assertThat(indexer.deleteIndexNames).containsExactly("kb_test_kb_chunks");
    }

    @Test
    @DisplayName("test_update_documents")
    void testUpdateDocuments() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, new FakeEmbedding(),
                null, new FakeChunker(), new FakeExtractor(), indexer, null, null, null);

        List<String> docIds = kb.updateDocuments(List.of(new Document("doc_1", "Updated document")));

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.deleteCalls).isGreaterThanOrEqualTo(1);
        assertThat(indexer.buildCalls).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("test_get_statistics_with_graph")
    void testGetStatisticsWithGraph() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null,
                null, null, null, indexer, null, null, null);

        Map<String, Object> stats = kb.getStatistics();

        assertThat(stats).containsEntry("kb_id", "test_kb");
        assertThat(stats).containsEntry("use_graph", true);
        assertThat(stats).containsKeys("chunk_index_info", "triple_index_info");
    }

    @Test
    @DisplayName("test_get_statistics_without_index_manager")
    void testGetStatisticsWithoutIndexManager() {
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true));

        Map<String, Object> stats = kb.getStatistics();

        assertThat(stats).containsEntry("kb_id", "test_kb");
        assertThat(stats).containsEntry("index_exists", false);
    }

    @Test
    @DisplayName("test_close")
    void testClose() throws Exception {
        CloseCountingGraphRetriever graphRetriever = new CloseCountingGraphRetriever();
        RecordingRetriever chunkRetriever = new RecordingRetriever(List.of());
        RecordingRetriever tripleRetriever = new RecordingRetriever(List.of());
        GraphKnowledgeBase kb = new GraphKnowledgeBase(config(true), null, null,
                null, null, null, null, null, chunkRetriever, tripleRetriever);
        Field field = GraphKnowledgeBase.class.getDeclaredField("graphRetriever");
        field.setAccessible(true);
        field.set(kb, graphRetriever);

        kb.close();

        assertThat(graphRetriever.closeCalls).isEqualTo(1);
        assertThat(chunkRetriever.closeCalls).isEqualTo(1);
        assertThat(tripleRetriever.closeCalls).isEqualTo(1);
    }

    private static KnowledgeBaseConfig config(boolean useGraph) {
        return new KnowledgeBaseConfig("test_kb", "vector", useGraph, 512, 50);
    }

    private static final class FakeParser extends Parser {
        private int parseCalls;

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            parseCalls++;
            return List.of(new Document("doc_1", "Test document 1"));
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return "Test document 1";
        }
    }

    private static final class FakeChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of("Test chunk 1");
        }

        @Override
        public List<TextChunk> chunkDocuments(List<Document> documents) {
            return List.of(new TextChunk("chunk_1", "Test chunk 1", "doc_1",
                    Map.of("chunk_id", "chunk_1", "doc_id", "doc_1"), null));
        }
    }

    private static final class FakeExtractor extends Extractor {
        private int extractCalls;

        @Override
        public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
            extractCalls++;
            return List.of(new Triple("Alice", "knows", "Bob", null, Map.of("doc_id", "doc_1")));
        }
    }

    private static final class FakeEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(0.1f, 0.2f, 0.3f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(text -> List.of(0.1f, 0.2f, 0.3f)).toList();
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class RecordingIndexer implements Indexer {
        private int buildCalls;
        private int deleteCalls;
        private final List<String> buildIndexNames = new java.util.ArrayList<>();
        private final List<String> deleteIndexNames = new java.util.ArrayList<>();

        @Override
        public boolean buildIndex(List<TextChunk> chunks, IndexConfig config,
                                  Embedding embedModel, Map<String, Object> options) {
            buildCalls++;
            buildIndexNames.add(config.getIndexName());
            return true;
        }

        @Override
        public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config,
                                   Embedding embedModel, Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
            deleteCalls++;
            deleteIndexNames.add(indexName);
            return true;
        }

        @Override
        public boolean indexExists(String indexName) {
            return true;
        }

        @Override
        public Map<String, Object> getIndexInfo(String indexName) {
            return Map.of("count", 10, "index_name", indexName);
        }

        @Override
        public String getDatabaseName() {
            return "db";
        }

        @Override
        public String getDistanceMetric() {
            return "cosine";
        }

        @Override
        public String getIndexType() {
            return "vector";
        }

        @Override
        public String getTextField() {
            return "text";
        }

        @Override
        public String getVectorField() {
            return "vector";
        }

        @Override
        public String getSparseVectorField() {
            return "sparse_vector";
        }

        @Override
        public String getMetadataField() {
            return "metadata";
        }

        @Override
        public String getDocIdField() {
            return "doc_id";
        }
    }

    private static class RecordingRetriever implements Retriever {
        private final List<RetrievalResult> results;
        private int retrieveCalls;
        private int closeCalls;

        private RecordingRetriever(List<RetrievalResult> results) {
            this.results = results;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold,
                                              String mode, Map<String, Object> options) {
            retrieveCalls++;
            return results;
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK,
                                                         String mode, Map<String, Object> options) {
            return queries.stream().map(query -> results).toList();
        }

        @Override
        public String getIndexType() {
            return "vector";
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class CloseCountingGraphRetriever extends GraphRetriever {
        private int closeCalls;

        private CloseCountingGraphRetriever() {
            super(new RecordingRetriever(List.of()), new RecordingRetriever(List.of()));
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class FakeModelClient extends BaseModelClient {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private int invokeCalls;

        private FakeModelClient(String... responses) {
            super(null, null);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected void validateConfig() {
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            invokeCalls++;
            return new AssistantMessage(responses.isEmpty() ? "[]" : responses.removeFirst());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark,
                                                     String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }
    }
}
