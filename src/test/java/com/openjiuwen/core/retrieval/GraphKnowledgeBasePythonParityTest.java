/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
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
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestGraphKnowledgeBase} tests in
 * {@code tests/unit_tests/core/retrieval/test_graph_knowledge_base.py}.
 */
class GraphKnowledgeBasePythonParityTest {

    @Test
    void parseFilesSuccessDelegatesToParser() {
        RecordingParser parser = new RecordingParser();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(graphConfig(), null, null, parser, null, null, null, null, null, null);

        List<Document> documents = kb.parseFiles(List.of("test1.txt")).join();

        assertThat(documents).hasSize(1);
        assertThat(parser.parseCalls).isEqualTo(1);
    }

    @Test
    void addDocumentsWithGraphBuildsChunkAndTripleIndexes() {
        RecordingIndexer indexer = new RecordingIndexer();
        RecordingExtractor extractor = new RecordingExtractor();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                graphConfig(),
                null,
                new StubEmbedding(),
                null,
                new RecordingChunker(),
                extractor,
                indexer,
                null,
                null,
                null
        );

        List<String> docIds = kb.addDocuments(List.of(new Document("doc_1", "Test document"))).join();

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.buildConfigs).extracting(IndexConfig::getIndexName)
                .containsExactly("kb_test_kb_chunks", "kb_test_kb_triples");
        assertThat(extractor.extractCalls).isEqualTo(1);
    }

    @Test
    void addDocumentsWithoutGraphBuildsOnlyChunkIndex() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                nonGraphConfig(),
                null,
                new StubEmbedding(),
                null,
                new RecordingChunker(),
                null,
                indexer,
                null,
                null,
                null
        );

        List<String> docIds = kb.addDocuments(List.of(new Document("doc_1", "Test document"))).join();

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.buildConfigs).extracting(IndexConfig::getIndexName)
                .containsExactly("kb_test_kb_chunks");
    }

    @Test
    void retrieveWithGraphReturnsGraphRetrieverResults() {
        RecordingRetriever chunkRetriever = RecordingRetriever.withResults(List.of(result("Test result", 0.95, "doc_1", "chunk_1")));
        RecordingRetriever tripleRetriever = RecordingRetriever.withResults(List.of());
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                graphConfig(),
                new FakeVectorStore(),
                new StubEmbedding(),
                null,
                null,
                null,
                null,
                null,
                chunkRetriever,
                tripleRetriever
        );

        List<RetrievalResult> results = kb.retrieve("test query", RetrievalConfig.builder().useGraph(true).topK(5).build()).join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Test result");
        assertThat(chunkRetriever.retrieveCalls).isGreaterThanOrEqualTo(1);
    }

    @Test
    void retrieveWithAgenticUsesAgenticRetrieverPath() {
        RecordingRetriever chunkRetriever = RecordingRetriever.withResults(List.of(result("Agentic result", 0.98, "doc_1", "chunk_1")));
        RecordingRetriever tripleRetriever = RecordingRetriever.withResults(List.of());
        FakeLlmClient llmClient = new FakeLlmClient(List.of("[]", "{\"sufficient\": true, \"next_question\": null}"));
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                graphConfig(),
                new FakeVectorStore(),
                new StubEmbedding(),
                null,
                null,
                null,
                null,
                llmClient,
                chunkRetriever,
                tripleRetriever
        );

        RetrievalConfig retrievalConfig = RetrievalConfig.builder().useGraph(true).agentic(true).topK(5).build();
        List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig).join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Agentic result");
        assertThat(llmClient.invokeCalls).isGreaterThanOrEqualTo(1);
    }

    @Test
    void retrieveWithoutGraphFallsBackToSimpleKnowledgeBasePath() {
        FakeVectorStore vectorStore = new FakeVectorStore(List.of(result("Simple result", 0.9, "doc_1", "chunk_1")));
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                nonGraphConfig(),
                vectorStore,
                new StubEmbedding(),
                null,
                null,
                null,
                null,
                new FakeLlmClient(List.of()),
                null,
                null
        );

        List<RetrievalResult> results = kb.retrieve("test query", RetrievalConfig.builder().useGraph(false).topK(5).build()).join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Simple result");
        assertThat(vectorStore.searchCalls).isEqualTo(1);
    }

    @Test
    void deleteDocumentsWithGraphDeletesChunkAndTripleIndexes() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(graphConfig(), null, null, null, null, null, indexer, null, null, null);

        Boolean result = kb.deleteDocuments(List.of("doc_1")).join();

        assertThat(result).isTrue();
        assertThat(indexer.deletedIndexNames).containsExactly("kb_test_kb_chunks", "kb_test_kb_triples");
    }

    @Test
    void deleteDocumentsWithoutGraphDeletesOnlyChunkIndex() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(nonGraphConfig(), null, null, null, null, null, indexer, null, null, null);

        Boolean result = kb.deleteDocuments(List.of("doc_1")).join();

        assertThat(result).isTrue();
        assertThat(indexer.deletedIndexNames).containsExactly("kb_test_kb_chunks");
    }

    @Test
    void updateDocumentsDeletesThenAddsDocuments() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                graphConfig(),
                null,
                new StubEmbedding(),
                null,
                new RecordingChunker(),
                new RecordingExtractor(),
                indexer,
                null,
                null,
                null
        );

        List<String> docIds = kb.updateDocuments(List.of(new Document("doc_1", "Updated document"))).join();

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.deletedIndexNames).contains("kb_test_kb_chunks");
        assertThat(indexer.buildConfigs).extracting(IndexConfig::getIndexName)
                .contains("kb_test_kb_chunks", "kb_test_kb_triples");
    }

    @Test
    void getStatisticsWithGraphIncludesChunkAndTripleInfo() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(graphConfig(), null, null, null, null, null, indexer, null, null, null);

        Map<String, Object> stats = kb.getStatistics().join();

        assertThat(stats).containsEntry("kb_id", "test_kb");
        assertThat(stats).containsEntry("use_graph", true);
        assertThat(stats).containsKeys("chunk_index_info", "triple_index_info");
        assertThat(indexer.infoIndexNames).containsExactly("kb_test_kb_chunks", "kb_test_kb_triples");
    }

    @Test
    void getStatisticsWithoutIndexManagerReportsNoIndex() {
        GraphKnowledgeBase kb = new GraphKnowledgeBase(graphConfig());

        Map<String, Object> stats = kb.getStatistics().join();

        assertThat(stats).containsEntry("kb_id", "test_kb");
        assertThat(stats).containsEntry("index_exists", false);
    }

    @Test
    void closeClosesGraphChunkAndTripleRetrievers() throws ReflectiveOperationException {
        RecordingRetriever chunkRetriever = RecordingRetriever.withResults(List.of());
        RecordingRetriever tripleRetriever = RecordingRetriever.withResults(List.of());
        CloseCountingGraphRetriever graphRetriever = new CloseCountingGraphRetriever();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                graphConfig(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                chunkRetriever,
                tripleRetriever
        );
        Field graphRetrieverField = GraphKnowledgeBase.class.getDeclaredField("graphRetriever");
        graphRetrieverField.setAccessible(true);
        graphRetrieverField.set(kb, graphRetriever);

        kb.close().join();

        assertThat(graphRetriever.closeCalls).isEqualTo(1);
        assertThat(chunkRetriever.closeCalls).isEqualTo(1);
        assertThat(tripleRetriever.closeCalls).isEqualTo(1);
    }

    private static KnowledgeBaseConfig graphConfig() {
        return KnowledgeBaseConfig.builder().kbId("test_kb").indexType("vector").useGraph(true).build();
    }

    private static KnowledgeBaseConfig nonGraphConfig() {
        return KnowledgeBaseConfig.builder().kbId("test_kb").indexType("vector").useGraph(false).build();
    }

    private static RetrievalResult result(String text, double score, String docId, String chunkId) {
        return new RetrievalResult(text, score, Map.of("doc_id", docId, "chunk_id", chunkId), docId, chunkId);
    }

    private static final class RecordingParser extends Parser {
        private int parseCalls;

        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            parseCalls++;
            return CompletableFuture.completedFuture(List.of(new Document("doc_1", "Test document 1")));
        }
    }

    private static final class RecordingChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }

        @Override
        public List<TextChunk> chunkDocuments(List<Document> documents) {
            return List.of(new TextChunk("chunk_1", "Test chunk 1", "doc_1", Map.of("chunk_id", "chunk_1")));
        }
    }

    private static final class RecordingExtractor extends Extractor {
        private int extractCalls;

        @Override
        public CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
            extractCalls++;
            return CompletableFuture.completedFuture(List.of(new Triple(
                    "Alice",
                    "knows",
                    "Bob",
                    Map.of("doc_id", "doc_1", "chunk_id", "chunk_1")
            )));
        }
    }

    private static final class RecordingIndexer extends Indexer {
        private final List<IndexConfig> buildConfigs = new ArrayList<>();
        private final List<String> deletedIndexNames = new ArrayList<>();
        private final List<String> infoIndexNames = new ArrayList<>();

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            buildConfigs.add(config);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            deletedIndexNames.add(indexName);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            infoIndexNames.add(indexName);
            return CompletableFuture.completedFuture(Map.of("count", 10));
        }
    }

    private static class RecordingRetriever implements Retriever {
        private final List<RetrievalResult> results;
        private int retrieveCalls;
        private int closeCalls;

        private RecordingRetriever(List<RetrievalResult> results) {
            this.results = List.copyOf(results);
        }

        private static RecordingRetriever withResults(List<RetrievalResult> results) {
            return new RecordingRetriever(results);
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            retrieveCalls++;
            int limit = Math.min(Math.max(topK, 0), results.size());
            return List.copyOf(results.subList(0, limit));
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(
                List<String> queries,
                int topK,
                String mode,
                Map<String, Object> options
        ) {
            return queries.stream().map(query -> retrieve(query, topK, null, mode, options)).toList();
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

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class StubEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1D, 0.1D, 0.1D));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(texts.stream().map(text -> List.of(0.1D, 0.1D, 0.1D)).toList());
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class FakeVectorStore implements VectorStore {
        private final List<RetrievalResult> searchResults;
        private int searchCalls;

        private FakeVectorStore() {
            this(List.of());
        }

        private FakeVectorStore(List<RetrievalResult> searchResults) {
            this.searchResults = List.copyOf(searchResults);
        }

        public FakeVectorStoreConfig getConfig() {
            return new FakeVectorStoreConfig("db");
        }

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(
                List<Double> queryVector,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            searchCalls++;
            return CompletableFuture.completedFuture(searchResults);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(
                String queryText,
                List<Double> queryVector,
                int topK,
                double alpha,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private record FakeVectorStoreConfig(String databaseName) {
        public String getDatabaseName() {
            return databaseName;
        }
    }

    private static final class FakeLlmClient extends BaseModelClient {
        private final ArrayDeque<String> responses;
        private int invokeCalls;

        private FakeLlmClient(List<String> responses) {
            super(
                    ModelRequestConfig.builder().modelName("fake").build(),
                    ModelClientConfig.builder()
                            .apiKey("fake-key")
                            .apiBase("http://localhost")
                            .verifySsl(false)
                            .build()
            );
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            invokeCalls++;
            String content = responses.isEmpty() ? "[]" : responses.removeFirst();
            return new AssistantMessage(content);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages,
                String model,
                String size,
                String negativePrompt,
                int n,
                boolean promptExtend,
                boolean watermark,
                int seed,
                Map<String, Object> kwargs
        ) {
            throw new UnsupportedOperationException("not used by GraphKnowledgeBase tests");
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages,
                String model,
                String voice,
                String languageType,
                Map<String, Object> kwargs
        ) {
            throw new UnsupportedOperationException("not used by GraphKnowledgeBase tests");
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages,
                String imgUrl,
                String audioUrl,
                String model,
                String size,
                String resolution,
                int duration,
                boolean promptExtend,
                boolean watermark,
                String negativePrompt,
                Integer seed,
                Map<String, Object> kwargs
        ) {
            throw new UnsupportedOperationException("not used by GraphKnowledgeBase tests");
        }
    }
}
