/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.indexing.indexer.InMemoryIndexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.SimpleTripleExtractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced retrieval system tests covering gaps identified in CHECK doc:
 * updateDocuments, retrieveMultiKbWithSource, Chunker behavior, TextChunk model,
 * and local graph/agentic retrieval paths.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class RetrievalAdvancedSystemTest {

    @TempDir
    Path tempDir;

    private static final String FAKE_PROVIDER = "fake";

    private static void registerFakeProvider(BaseModelClient client) {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return FAKE_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return client;
            }
        });
    }

    static class RecordingParser extends Parser {
        private final List<String> parsedDocs = new ArrayList<>();
        private final List<Map<String, Object>> parsedOptions = new ArrayList<>();

        @Override
        public CompletableFuture<List<Document>> parseAsync(String doc, String docId,
                                                             BaseModelClient llmClient,
                                                             Map<String, Object> options) {
            parsedDocs.add(doc);
            parsedOptions.add(new LinkedHashMap<>(options));
            return CompletableFuture.completedFuture(List.of(new Document(docId, "parsed:" + doc, Map.of("path", doc))));
        }

        @Override
        protected CompletableFuture<String> parseContent(String doc, BaseModelClient llmClient,
                                                          Map<String, Object> options) {
            return CompletableFuture.completedFuture(doc);
        }

        @Override
        public boolean supports(String doc) {
            return doc != null && (doc.startsWith("http://") || doc.startsWith("https://"));
        }
    }

    static class QueueModelClient extends BaseModelClient {
        private final List<String> responses;
        private int invocationCount;

        QueueModelClient(String... responses) {
            super(
                    ModelRequestConfig.builder().modelName("fake-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("fake")
                            .apiKey("fake-key")
                            .apiBase("http://localhost")
                            .verifySsl(false)
                            .build());
            this.responses = new ArrayList<>(List.of(responses));
        }

        int getInvocationCount() {
            return invocationCount;
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
                                       Map<String, Object> kwargs) throws Exception {
            invocationCount++;
            String content = responses.isEmpty() ? "[]" : responses.remove(0);
            return new AssistantMessage(content);
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
                                                      Map<String, Object> kwargs) throws Exception {
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
                                                     Map<String, Object> kwargs) throws Exception {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                      String model,
                                                      String voice,
                                                      String languageType,
                                                      Map<String, Object> kwargs) throws Exception {
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
                                                     Map<String, Object> kwargs) throws Exception {
            return null;
        }
    }

    private SimpleKnowledgeBase createTestKB(String kbName, String collectionName) {
        HashEmbedding embedding = new HashEmbedding(32, 256);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore(collectionName);
        InMemoryIndexer indexer = new InMemoryIndexer(vectorStore);
        return new SimpleKnowledgeBase(
                new KnowledgeBaseConfig(kbName),
                vectorStore, embedding, null,
                new CharChunker(512, 64), indexer, null, null);
    }

    private GraphKnowledgeBase createGraphKnowledgeBase(String kbId, BaseModelClient llmClient) {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("graph_store_" + kbId);
        InMemoryIndexer indexer = new InMemoryIndexer(vectorStore);
        return new GraphKnowledgeBase(
                new KnowledgeBaseConfig(kbId, "vector", true, 64, 8),
                vectorStore,
                new HashEmbedding(32, 256),
                null,
                new CharChunker(64, 8),
                new SimpleTripleExtractor(),
                indexer,
                llmClient,
                null,
                null);
    }

    @Nested
    @DisplayName("KnowledgeBase Parsing Tests")
    class KnowledgeBaseParsingTests {

        @Test
        @DisplayName("parseFiles delegates to parser and injects file_name")
        void testParseFiles() {
            RecordingParser parser = new RecordingParser();
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    new KnowledgeBaseConfig("parse_file_kb"),
                    null, null, parser, null, null, null, null);

            List<String> filePaths = List.of(
                    tempDir.resolve("alpha.txt").toString(),
                    tempDir.resolve("beta.md").toString());

            List<Document> documents = kb.parseFiles(filePaths);

            assertEquals(2, documents.size());
            assertEquals(2, parser.parsedDocs.size());
            assertEquals("alpha.txt", parser.parsedOptions.get(0).get("file_name"));
            assertEquals("beta.md", parser.parsedOptions.get(1).get("file_name"));
        }

        @Test
        @DisplayName("parseUrls skips unsupported URLs")
        void testParseUrls() {
            RecordingParser parser = new RecordingParser();
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    new KnowledgeBaseConfig("parse_url_kb"),
                    null, null, parser, null, null, null, null);

            List<Document> documents = kb.parseUrls(List.of(
                    "https://example.com/guide",
                    "mailto:test@example.com"));

            assertEquals(1, documents.size());
            assertEquals(1, parser.parsedDocs.size());
            assertEquals("https://example.com/guide", parser.parsedDocs.get(0));
        }
    }

    @Nested
    @DisplayName("UpdateDocuments Tests")
    class UpdateDocumentsTests {

        @Test
        @DisplayName("updateDocuments adds new documents")
        void testUpdateDocumentsAdd() {
            SimpleKnowledgeBase kb = createTestKB("update_kb_1", "update_coll_1");

            List<Document> docs = List.of(
                    new Document("upd_doc1", "OpenJiuWen是一个智能体框架"),
                    new Document("upd_doc2", "支持多种AI模型集成"));

            List<String> ids = kb.updateDocuments(docs);
            assertNotNull(ids);
            assertFalse(ids.isEmpty(), "updateDocuments should return doc IDs");
            System.out.println("[UpdateDocs Add] IDs: " + ids);
        }

        @Test
        @DisplayName("updateDocuments overwrites existing documents")
        void testUpdateDocumentsOverwrite() {
            SimpleKnowledgeBase kb = createTestKB("update_kb_2", "update_coll_2");

            // Add initial document
            kb.addDocuments(List.of(new Document("doc_overwrite", "Original content")));

            // Update with new content
            List<String> ids = kb.updateDocuments(
                    List.of(new Document("doc_overwrite", "Updated content")));
            assertNotNull(ids);
            assertFalse(ids.isEmpty());
            System.out.println("[UpdateDocs Overwrite] IDs: " + ids);
        }
    }

    @Nested
    @DisplayName("GraphKnowledgeBase Lifecycle Tests")
    class GraphKnowledgeBaseLifecycleTests {

        @Test
        @DisplayName("GraphKnowledgeBase builds chunk and triple indexes")
        void testGraphKnowledgeBaseLifecycleWithGraph() {
            String kbId = "graph_kb_" + UUID.randomUUID().toString().replace("-", "");
            InMemoryVectorStore store = new InMemoryVectorStore("graph_store_" + kbId);
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    new KnowledgeBaseConfig(kbId, "vector", true, 64, 8),
                    store,
                    new HashEmbedding(32, 256),
                    null,
                    new CharChunker(64, 8),
                    new SimpleTripleExtractor(),
                    new InMemoryIndexer(store),
                    null,
                    null,
                    null);

            List<String> addedIds = kb.addDocuments(List.of(
                    new Document("graph_doc", "Alice knows Bob. Bob mentors Carol.")));
            assertEquals(List.of("graph_doc"), addedIds);

            List<RetrievalResult> results = kb.retrieve("Alice knows", new RetrievalConfig());
            assertFalse(results.isEmpty(), "graph retrieval should return local matches");

            Map<String, Object> stats = kb.getStatistics();
            assertNotNull(stats.get("chunk_index_info"));
            assertNotNull(stats.get("triple_index_info"));

            List<String> updatedIds = kb.updateDocuments(List.of(
                    new Document("graph_doc", "Alice teaches Carol. Carol trusts Alice.")));
            assertEquals(List.of("graph_doc"), updatedIds);
            assertTrue(kb.deleteDocuments(List.of("graph_doc")));
        }

        @Test
        @DisplayName("GraphKnowledgeBase falls back to chunk index when graph is disabled")
        void testGraphKnowledgeBaseWithoutGraphUsesChunkIndexOnly() {
            String kbId = "plain_kb_" + UUID.randomUUID().toString().replace("-", "");
            InMemoryVectorStore store = new InMemoryVectorStore("plain_store_" + kbId);
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    new KnowledgeBaseConfig(kbId, "vector", false, 64, 8),
                    store,
                    new HashEmbedding(32, 256),
                    null,
                    new CharChunker(64, 8),
                    new SimpleTripleExtractor(),
                    new InMemoryIndexer(store),
                    null,
                    null,
                    null);

            kb.addDocuments(List.of(new Document("plain_doc", "OpenJiuWen supports local retrieval.")));

            List<RetrievalResult> results = kb.retrieve("local retrieval", new RetrievalConfig());
            assertFalse(results.isEmpty(), "chunk-only retrieval should still work");

            Map<String, Object> stats = kb.getStatistics();
            assertNotNull(stats.get("chunk_index_info"));
            assertFalse(stats.containsKey("triple_index_info"));
        }
    }

    @Nested
    @DisplayName("Agentic Graph Retrieval Tests")
    class AgenticGraphRetrievalTests {

        @Test
        @DisplayName("GraphKnowledgeBase agentic retrieval runs the local LLM-guided graph path")
        void testGraphKnowledgeBaseAgenticRetrievalRunsLocalGraphPath() {
            registerFakeProvider(null);
            QueueModelClient llmClient = new QueueModelClient(
                    "[[\"Alice\",\"mentors\",\"Bob\"]]",
                    "[[\"Alice\",\"mentors\",\"Bob\"]]",
                    "{\"sufficient\":true,\"next_question\":null}");
            String kbId = "agentic_kb_" + UUID.randomUUID().toString().replace("-", "");
            GraphKnowledgeBase kb = createGraphKnowledgeBase(kbId, llmClient);

            kb.addDocuments(List.of(
                    new Document("agent_doc_1", "Alice mentors Bob."),
                    new Document("agent_doc_2", "Bob founded ExampleCo.")));

            RetrievalConfig retrievalConfig = new RetrievalConfig();
            retrievalConfig.setAgentic(true);
            retrievalConfig.setGraphExpansion(true);
            retrievalConfig.setTopK(3);

            List<RetrievalResult> results = kb.retrieve("Tell me about Bob and ExampleCo", retrievalConfig);

            assertFalse(results.isEmpty(), "agentic graph retrieval should return fused local matches");
            assertEquals(3, llmClient.getInvocationCount(),
                    "agentic graph retrieval should perform read/read/rewrite locally");
            assertTrue(results.stream().anyMatch(result -> result.getText().contains("Bob")),
                    "returned passages should come from the locally indexed graph documents");
        }
    }

    @Nested
    @DisplayName("RetrieveMultiKbWithSource Tests")
    class MultiKBWithSourceTests {

        @Test
        @DisplayName("retrieveMultiKbWithSource returns results with KB source")
        void testRetrieveMultiKbWithSource() {
            SimpleKnowledgeBase kb1 = createTestKB("source_kb_1", "source_coll_1");
            kb1.addDocuments(List.of(
                    new Document("s_d1", "智能体开发框架"),
                    new Document("s_d2", "工具集成能力")));

            SimpleKnowledgeBase kb2 = createTestKB("source_kb_2", "source_coll_2");
            kb2.addDocuments(List.of(
                    new Document("s_d3", "Java版本智能体核心"),
                    new Document("s_d4", "RAG检索增强")));

            List<MultiKBRetrievalResult> results = SimpleKnowledgeBase
                    .retrieveMultiKbWithSource(List.of(kb1, kb2), "智能体", new RetrievalConfig(), 3);

            assertNotNull(results);
            System.out.println("[MultiKBWithSource] Results: " + results.size());
            for (MultiKBRetrievalResult r : results) {
                assertNotNull(r.getKbIds(), "kbIds should not be null");
                System.out.println("  - Text: " + r.getText().substring(0, Math.min(30, r.getText().length()))
                        + "... kbIds: " + r.getKbIds());
            }
        }
    }

    @Nested
    @DisplayName("CharChunker Tests")
    class CharChunkerTests {

        @Test
        @DisplayName("CharChunker splits text into overlapping chunks")
        void testCharChunkerBasic() {
            CharChunker chunker = new CharChunker(10, 2);
            List<String> chunks = chunker.chunkText("abcdefghijklmnopqrstuvwxyz");

            assertNotNull(chunks);
            assertFalse(chunks.isEmpty());
            assertEquals(10, chunks.get(0).length(), "First chunk should be chunkSize");
            System.out.println("[CharChunker] Chunks: " + chunks);

            // Verify overlap: end of chunk[0] should overlap with start of chunk[1]
            if (chunks.size() > 1) {
                String end0 = chunks.get(0).substring(chunks.get(0).length() - 2);
                String start1 = chunks.get(1).substring(0, 2);
                assertEquals(end0, start1, "Chunks should overlap by chunkOverlap chars");
            }
        }

        @Test
        @DisplayName("CharChunker handles empty text")
        void testCharChunkerEmpty() {
            CharChunker chunker = new CharChunker(100, 10);
            List<String> chunks = chunker.chunkText("");
            assertNotNull(chunks);
            assertTrue(chunks.isEmpty(), "Empty text should produce no chunks");
        }

        @Test
        @DisplayName("CharChunker handles text shorter than chunkSize")
        void testCharChunkerShortText() {
            CharChunker chunker = new CharChunker(100, 10);
            List<String> chunks = chunker.chunkText("short text");
            assertNotNull(chunks);
            assertEquals(1, chunks.size(), "Short text should produce single chunk");
            assertEquals("short text", chunks.get(0));
        }

        @Test
        @DisplayName("CharChunker chunkDocuments produces TextChunks")
        void testCharChunkerDocuments() {
            CharChunker chunker = new CharChunker(20, 5);
            List<Document> docs = List.of(
                    new Document("doc_a", "This is the first document with enough text to chunk."),
                    new Document("doc_b", "Second doc."));

            List<TextChunk> textChunks = chunker.chunkDocuments(docs);
            assertNotNull(textChunks);
            assertFalse(textChunks.isEmpty());
            for (TextChunk tc : textChunks) {
                assertNotNull(tc.getId(), "TextChunk should have an ID");
                assertNotNull(tc.getText(), "TextChunk should have text");
                assertNotNull(tc.getDocId(), "TextChunk should reference parent doc");
            }
            System.out.println("[CharChunker Docs] TextChunks: " + textChunks.size());
        }
    }

    @Nested
    @DisplayName("TextChunk Model Tests")
    class TextChunkTests {

        @Test
        @DisplayName("TextChunk.fromDocument creates chunk with metadata")
        void testTextChunkFromDocument() {
            Document doc = new Document("src_doc", "Some chunk text",
                    Map.of("source", "test", "category", "retrieval"));

            TextChunk chunk = TextChunk.fromDocument(doc, "Some chunk text");

            assertNotNull(chunk);
            assertNotNull(chunk.getId());
            assertEquals("Some chunk text", chunk.getText());
            assertEquals("src_doc", chunk.getDocId());
            System.out.println("[TextChunk] Created from doc: " + chunk.getId());
        }

        @Test
        @DisplayName("TextChunk auto-generates ID when not provided")
        void testTextChunkAutoId() {
            Document doc = new Document("parent_doc", "Full text");
            TextChunk chunk = TextChunk.fromDocument(doc, "Chunk portion");

            assertNotNull(chunk.getId(), "ID should be auto-generated");
            assertFalse(chunk.getId().isEmpty());
        }
    }
}
