/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.retrieval.common.*;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.retriever.*;
import com.openjiuwen.core.retrieval.utils.ConfigManager;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Core retrieval regression tests ported from Python retrieval unit tests.
 */
class RetrievalCoreTest {

    @Nested
    class ConfigAndModelTests {

        @Test
        @DisplayName("configs and common models")
        void testConfigsAndCommonModels() {
            KnowledgeBaseConfig kbConfig = new KnowledgeBaseConfig("test_kb");
            assertEquals("hybrid", kbConfig.getIndexType());
            assertFalse(kbConfig.isUseGraph());

            RetrievalConfig retrievalConfig = new RetrievalConfig();
            assertEquals(5, retrievalConfig.getTopK());
            assertNull(retrievalConfig.getScoreThreshold());

            IndexConfig indexConfig = new IndexConfig("idx", "vector");
            assertEquals("vector", indexConfig.getIndexType());

            EmbeddingConfig embeddingConfig = new EmbeddingConfig("test-model", "https://api.example.com");
            assertEquals("test-model", embeddingConfig.getModelName());

            Document document = new Document("doc_1", "Test document", Map.of("source", "test"));
            TextChunk chunk = TextChunk.fromDocument(document, "Test chunk", "chunk_1");
            SearchResult searchResult = new SearchResult("result_1", "Hello", 0.95, Map.of("doc_id", "doc_1"));
            RetrievalResult retrievalResult = new RetrievalResult("Hello", 0.95, Map.of("doc_id", "doc_1"), "doc_1", "chunk_1");
            Triple triple = new Triple("Alice", "knows", "Bob", 0.9, Map.of("doc_id", "doc_1"));

            assertEquals("doc_1", chunk.getDocId());
            assertEquals("Hello", searchResult.getText());
            assertEquals("chunk_1", retrievalResult.getChunkId());
            assertEquals("Bob", triple.getObject());

            assertThrows(BaseError.class, () -> {
                KnowledgeBaseConfig invalid = new KnowledgeBaseConfig();
                invalid.validate();
            });
            assertThrows(BaseError.class, () -> new KnowledgeBaseConfig("test", "invalid", false, 1, 0));
            assertThrows(BaseError.class, () -> new IndexConfig("idx", "invalid"));
            assertThrows(BaseError.class, () -> new Document(null, null, null));
            assertThrows(BaseError.class, () -> new TextChunk(null, "text", "doc"));
            assertThrows(BaseError.class, () -> new SearchResult(null, "text", 0.1));
            assertThrows(BaseError.class, () -> new RetrievalResult(null, 0.1));
            assertThrows(BaseError.class, () -> new Triple(null, "knows", "Bob"));
        }

        @Test
        @DisplayName("multimodal document")
        void testMultimodalDocument(@TempDir Path tempDir) throws Exception {
            Path imageFile = tempDir.resolve("test.png");
            Files.write(imageFile, new byte[]{1, 2, 3});
            Path textFile = tempDir.resolve("test.txt");
            Files.writeString(textFile, "hello");

            MultimodalDocument doc = new MultimodalDocument();
            doc.addField("text", "hello world");
            doc.addField("image", "data:image/png;base64,AA==", null, "a".repeat(32));
            doc.addField("text", null, textFile, "");
            doc.addField("image", null, imageFile, "");

            assertEquals(4, doc.getContent().size());
            assertEquals("text", doc.getContent().get(0).get("type"));
            assertEquals("a".repeat(32), doc.getContent().get(1).get("uuid"));
            assertTrue(((Map<?, ?>) doc.getContent().get(3).get("image_url")).get("url").toString().startsWith("data:image/"));

            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("invalid", "v"));
            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("image", null, null, ""));
            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("image", "data:image/png;base64,AA==", imageFile, ""));
            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("image", "bad", null, ""));
            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("image", null, "not_a_path", ""));
            assertThrows(BaseError.class, () -> new MultimodalDocument().addField("image", "data:image/png;base64,AA==", null, "a".repeat(33)));
        }

        @Test
        @DisplayName("triple memory and beam")
        void testTripleMemoryAndBeam() {
            TripleMemory memory = new TripleMemory();
            memory.extendMemory(List.of("Alice", "knows", "Bob"));
            memory.extendMemory(List.of("alice", "knows", "bob"));
            assertEquals(1, memory.size());
            assertTrue(memory.getTriplesStr().contains("(Alice knows Bob)"));

            RetrievalResult r1 = new RetrievalResult("A", 0.9);
            RetrievalResult r2 = new RetrievalResult("B", 0.8);
            TripleBeam beam = new TripleBeam(List.of(r1, r2), 0.7);
            assertEquals(2, beam.size());
            assertTrue(beam.contains(new RetrievalResult("A", 0.1)));
            assertEquals(0.7, beam.getScore());
        }
    }

    @Nested
    class UtilityTests {

        @Test
        @DisplayName("rrf fusion keeps type and deduplicates")
        void testRrfFusion() {
            List<RetrievalResult> results1 = new ArrayList<>();
            results1.add(new RetrievalResult("Result 1", 0.9));
            results1.add(new RetrievalResult("Result 2", 0.8));
            List<RetrievalResult> results2 = new ArrayList<>();
            results2.add(new RetrievalResult("Result 2", 0.85));
            results2.add(new RetrievalResult("Result 3", 0.7));
            List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of(results1, results2), 60);
            assertEquals(3, fused.size());
            assertEquals("Result 2", fused.get(0).getText());

            List<SearchResult> searchResults = FusionUtils.rrfFusionSearch(List.of(
                    List.of(new SearchResult("1", "S1", 0.9), new SearchResult("2", "S2", 0.8))), 60);
            assertEquals(2, searchResults.size());
            assertInstanceOf(SearchResult.class, searchResults.get(0));
        }

        @Test
        @DisplayName("rrf fusion handles empty and single lists")
        void testRrfFusionEdgeCases() {
            List<RetrievalResult> emptyFused = FusionUtils.rrfFusionRetrieval(List.of(), 60);
            assertTrue(emptyFused.isEmpty());

            List<RetrievalResult> singleList = new ArrayList<>();
            singleList.add(new RetrievalResult("Only", 0.9));
            List<RetrievalResult> singleFused = FusionUtils.rrfFusionRetrieval(List.of(singleList), 60);
            assertEquals(1, singleFused.size());
            assertEquals("Only", singleFused.get(0).getText());

            List<RetrievalResult> withEmpty = FusionUtils.rrfFusionRetrieval(
                    List.of(singleList, new ArrayList<>()), 60);
            assertEquals(1, withEmpty.size());
        }

        @Test
        @DisplayName("config manager json and yaml")
        void testConfigManager(@TempDir Path tempDir) throws Exception {
            Path json = tempDir.resolve("kb.json");
            Files.writeString(json, "{\"kb_id\":\"test_kb\",\"index_type\":\"vector\",\"use_graph\":false}");
            Path yaml = tempDir.resolve("kb.yaml");
            Files.writeString(yaml, "kb_id: test_kb\nindex_type: hybrid\n");

            ConfigManager manager = new ConfigManager(json.toString());
            assertEquals("test_kb", manager.getKnowledgeBaseConfig().getKbId());
            assertEquals("vector", manager.getKnowledgeBaseConfig().getIndexType());

            ConfigManager yamlManager = new ConfigManager();
            yamlManager.loadFromFile(yaml.toString());
            assertEquals("hybrid", yamlManager.getKnowledgeBaseConfig().getIndexType());

            manager.updateConfig(new KnowledgeBaseConfig("kb2"));
            assertNotNull(manager.getConfig(KnowledgeBaseConfig.class));

            assertThrows(BaseError.class, () -> new ConfigManager().loadFromFile(tempDir.resolve("missing.json").toString()));
        }
    }

    @Nested
    class RetrieverTests {

        @Test
        @DisplayName("vector sparse hybrid retrievers")
        void testVectorSparseHybridRetrievers() {
            StubVectorStore store = new StubVectorStore();
            store.vectorResults = List.of(
                    new SearchResult("1", "Result 1", 0.95, Map.of("doc_id", "doc_1", "chunk_id", "chunk_1")),
                    new SearchResult("2", "Result 2", 0.85, Map.of("doc_id", "doc_2", "chunk_id", "chunk_2")));
            store.sparseResults = List.of(new SearchResult("3", "Sparse result", 0.8, Map.of()));
            store.hybridResults = List.of(
                    new SearchResult("1", "Hybrid result 1", 0.95, Map.of("doc_id", "doc_1")),
                    new SearchResult("2", "Hybrid result 2", 0.85, Map.of("doc_id", "doc_2")));
            Embedding embedding = new MapEmbedding(Map.of("test query", List.of(1f, 0f, 0f)));

            VectorRetriever vectorRetriever = new VectorRetriever(store, embedding);
            assertEquals(2, vectorRetriever.retrieve("test query", 5, null, "vector", Map.of()).size());
            assertEquals(1, vectorRetriever.retrieve("test query", 5, 0.9, "vector", Map.of()).size());

            store.vectorResults = List.of();
            assertEquals("Sparse result", vectorRetriever.retrieve("test query", 5, null, "vector", Map.of()).get(0).getText());
            assertThrows(BaseError.class, () -> vectorRetriever.retrieve("test query", 5, null, "sparse", Map.of()));
            assertThrows(BaseError.class, () -> new VectorRetriever(store, null).retrieve("test query", 5, null, "vector", Map.of()));
            assertEquals(2, vectorRetriever.batchRetrieve(List.of("q1", "q2"), 5, "vector", Map.of()).size());

            SparseRetriever sparseRetriever = new SparseRetriever(store);
            assertEquals(1, sparseRetriever.retrieve("test query", 5, null, "sparse", Map.of()).size());
            assertThrows(BaseError.class, () -> sparseRetriever.retrieve("test query", 5, null, "vector", Map.of()));

            store.vectorResults = List.of(new SearchResult("1", "Vector result", 0.9, Map.of()));
            HybridRetriever hybridRetriever = new HybridRetriever(store, embedding, 0.7);
            assertEquals(2, hybridRetriever.retrieve("test query", 5, null, "hybrid", Map.of()).size());
            assertEquals(1, hybridRetriever.retrieve("test query", 5, null, "vector", Map.of()).size());
            assertEquals(1, hybridRetriever.retrieve("test query", 5, null, "sparse", Map.of()).size());
            List<RetrievalResult> vectorThreshold = hybridRetriever.retrieve("test query", 5, 0.85, "vector", Map.of());
            assertEquals(1, vectorThreshold.size());
            assertThrows(BaseError.class, () -> hybridRetriever.retrieve("test query", 5, 0.5, "hybrid", Map.of()));
            assertThrows(BaseError.class, () -> new HybridRetriever(store, null).retrieve("test query", 5, null, "vector", Map.of()));
            hybridRetriever.retrieve("test query", 5, null, "hybrid", Map.of("alpha", 0.8));
            assertEquals(0.8, store.lastAlpha);
            assertEquals(2, hybridRetriever.batchRetrieve(List.of("q1", "q2"), 5, "hybrid", Map.of()).size());
        }

        @Test
        @DisplayName("graph retriever and beam search")
        void testGraphRetrieverAndBeamSearch() {
            StubVectorStore store = new StubVectorStore();
            store.collectionName = "chunks";
            GraphRetriever graphRetriever = new GraphRetriever(store, new MapEmbedding(Map.of("test query", List.of(1f, 0f, 0f))), "chunks", "triples");
            assertThrows(BaseError.class, () -> graphRetriever.retrieve("test query", 5, 0.8, "sparse", Map.of()));
            assertTrue(graphRetriever.graphExpansion("test query", List.of(), null, 5, "hybrid", Map.of()).isEmpty());

            Retriever chunkRetriever = mock(Retriever.class);
            Retriever tripleRetriever = mock(Retriever.class);
            GraphRetriever closeable = new GraphRetriever(chunkRetriever, tripleRetriever);
            closeable.close();

            Retriever embedlessRetriever = mock(Retriever.class);
            assertThrows(BaseError.class, () -> new TripleBeamSearch(embedlessRetriever, 2, 10, 0));

            StubVectorStore tripleStore = new StubVectorStore();
            tripleStore.vectorResults = List.of();
            MapEmbedding embedding = new MapEmbedding(Map.of(
                    "entity1 relation entity2", List.of(0.1f, 0.2f, 0.3f),
                    "entity3 relation entity4", List.of(0.2f, 0.3f, 0.4f),
                    "test query", List.of(0.15f, 0.25f, 0.35f)));
            TripleBeamSearch search = new TripleBeamSearch(new VectorRetriever(tripleStore, embedding), 2, 5, 2);
            List<TripleBeam> beams = search.beamSearch("test query", List.of(
                    new RetrievalResult("entity1 relation entity2", 0.9, Map.of("triple", "[\"entity1\",\"relation\",\"entity2\"]"), null, null),
                    new RetrievalResult("entity3 relation entity4", 0.8, Map.of("triple", "[\"entity3\",\"relation\",\"entity4\"]"), null, null)));
            assertEquals(2, beams.size());
            assertEquals(1, beams.get(0).size());
        }

        @Test
        @DisplayName("agentic retriever generic and graph")
        void testAgenticRetriever() {
            Retriever baseRetriever = mock(Retriever.class);
            when(baseRetriever.getIndexType()).thenReturn("hybrid");
            when(baseRetriever.retrieve(eq("original query"), eq(5), eq(null), eq("hybrid"), any()))
                    .thenReturn(List.of(new RetrievalResult("Result 1", 0.9)));
            when(baseRetriever.retrieve(eq("rewritten query"), eq(5), eq(null), eq("hybrid"), any()))
                    .thenReturn(List.of(new RetrievalResult("Result 2", 0.8)));

            TestLlmClient llm = new TestLlmClient(
                    "[]",
                    "{\"sufficient\": false, \"next_question\": \"rewritten query\"}",
                    "[]");
            AgenticRetriever agenticRetriever = new AgenticRetriever(baseRetriever, llm, 2);
            assertFalse(agenticRetriever.isGraphRetriever());
            assertEquals("hybrid", agenticRetriever.getDefaultMode());
            List<RetrievalResult> genericResults = agenticRetriever.retrieve("original query", 5, null, null, Map.of());
            assertEquals(2, genericResults.size());

            GraphRetriever graph = Mockito.mock(GraphRetriever.class);
            Retriever chunkRetriever = mock(Retriever.class);
            when(graph.getIndexType()).thenReturn("hybrid");
            when(graph.getRetrieverForMode("hybrid", true)).thenReturn(chunkRetriever);
            when(chunkRetriever.retrieve(any(), eq(5), eq(null), eq("hybrid"), any()))
                    .thenReturn(List.of(new RetrievalResult("Chunk result", 0.9)));
            when(graph.graphExpansion(any(), any(), any(), eq(5), eq("hybrid"), any()))
                    .thenReturn(List.of(new RetrievalResult("Expanded result", 0.95)));
            when(graph.getRetrieverForMode("hybrid", false)).thenReturn(mock(Retriever.class));

            TestLlmClient graphLlm = new TestLlmClient(
                    "[]",
                    "[]",
                    "{\"sufficient\": true, \"next_question\": null}");
            AgenticRetriever graphAgentic = new AgenticRetriever(graph, graphLlm, 2);
            assertTrue(graphAgentic.isGraphRetriever());
            assertFalse(graphAgentic.retrieve("graph query", 5, null, null, Map.of()).isEmpty());
            assertThrows(BaseError.class, () -> graphAgentic.retrieve("graph query", 0, null, null, Map.of()));

            graphAgentic.close();
        }
    }

    private static final class StubVectorStore implements VectorStore {
        private String collectionName = "test_collection";
        private List<SearchResult> vectorResults = List.of();
        private List<SearchResult> sparseResults = List.of();
        private List<SearchResult> hybridResults = List.of();
        private double lastAlpha;

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            StubVectorStore copy = new StubVectorStore();
            copy.collectionName = collectionName;
            copy.vectorResults = vectorResults;
            copy.sparseResults = sparseResults;
            copy.hybridResults = hybridResults;
            copy.lastAlpha = lastAlpha;
            return copy;
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options) {
            return vectorResults;
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options) {
            return sparseResults;
        }

        @Override
        public List<SearchResult> hybridSearch(String queryText,
                                               List<Float> queryVector,
                                               int topK,
                                               double alpha,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            this.lastAlpha = alpha;
            return hybridResults;
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean tableExists(String tableName) {
            return true;
        }

        @Override
        public void deleteTable(String tableName) {
        }

        @Override
        public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            return vectorResults;
        }

        @Override
        public long count(String tableName) {
            return 0;
        }

        @Override
        public String getDatabaseName() {
            return "";
        }

        @Override
        public String getDistanceMetric() {
            return "cosine";
        }

        @Override
        public String getIndexType() {
            return "hybrid";
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

    private static final class MapEmbedding implements Embedding {
        private final Map<String, List<Float>> embeddings;

        private MapEmbedding(Map<String, List<Float>> embeddings) {
            this.embeddings = new LinkedHashMap<>(embeddings);
        }

        @Override
        public List<Float> embedQuery(String text) {
            return embeddings.getOrDefault(text, List.of(1f, 0f, 0f));
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            List<List<Float>> result = new ArrayList<>();
            for (String text : texts) {
                result.add(embedQuery(text));
            }
            return result;
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class TestLlmClient extends BaseModelClient {
        private final Queue<String> responses = new ArrayDeque<>();

        private TestLlmClient(String... responses) {
            super(
                    ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("test")
                            .apiKey("key")
                            .apiBase("http://localhost")
                            .verifySsl(false)
                            .build());
            this.responses.addAll(List.of(responses));
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            return new AssistantMessage(responses.isEmpty() ? "" : responses.remove());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                     String model,
                                                     String size,
                                                     String negativePrompt,
                                                     int n,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     int seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                      String model,
                                                      String voice,
                                                      String languageType,
                                                      Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages,
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
                                                     Map<String, Object> kwargs) {
            return null;
        }
    }
}
