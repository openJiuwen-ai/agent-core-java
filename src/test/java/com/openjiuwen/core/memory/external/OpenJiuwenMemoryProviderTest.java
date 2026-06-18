/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the OpenJiuwen long-term memory provider.
 *
 * <p>Mirrors Python's {@code OpenJiuwenMemoryProvider} in
 * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.</p>
 */
class OpenJiuwenMemoryProviderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nameAvailabilitySchemasAndPromptMirrorPythonSurface() {
        OpenJiuwenMemoryProvider emptyConfig = new OpenJiuwenMemoryProvider();
        assertEquals("openjiuwen", emptyConfig.getName());
        assertFalse(emptyConfig.isAvailable());

        OpenJiuwenMemoryProvider withEmbeddingConfig = new OpenJiuwenMemoryProvider(Map.of(
                "embedding", Map.of("model_name", "embed-model")));
        assertTrue(withEmbeddingConfig.isAvailable());

        OpenJiuwenMemoryProvider injected = provider(new RecordingLongTermMemory());
        assertTrue(injected.isAvailable());

        List<Map<String, Object>> schemas = injected.getToolSchemas();
        assertEquals(List.of("ltm_search", "ltm_search_summary"),
                schemas.stream().map(schema -> String.valueOf(schema.get("name"))).toList());
        Map<String, Object> searchProperties = properties(schemas.get(0));
        assertEquals(5, ((Number) ((Map<?, ?>) searchProperties.get("num")).get("default")).intValue());
        assertEquals(0.3d, ((Number) ((Map<?, ?>) searchProperties.get("threshold")).get("default")).doubleValue());
        assertEquals(List.of("query"), required(schemas.get(0)));
        assertEquals(3, ((Number) ((Map<?, ?>) properties(schemas.get(1)).get("num")).get("default")).intValue());

        String prompt = injected.systemPromptBlock();
        assertTrue(prompt.contains("Long-Term Memory System"));
        assertTrue(prompt.contains("ltm_search"));
        assertTrue(prompt.contains("Conversation summaries"));
    }

    @Test
    void notInitializedToolCallReturnsJsonError() throws Exception {
        OpenJiuwenMemoryProvider provider = provider(new RecordingLongTermMemory());

        Map<?, ?> payload = json(provider.handleToolCall("ltm_search", Map.of("query", "x")).join());

        assertEquals("Memory provider not initialized", payload.get("error"));
    }

    @Test
    void initializeRegistersStoresScopeAndRuntimeIds() {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        MemoryScopeConfig scopeConfig = new MemoryScopeConfig();
        OpenJiuwenMemoryProvider provider = provider(memory, scopeConfig);

        provider.initialize(Map.of(
                "user_id", "u1",
                "scope_id", "s1",
                "session_id", "sess1")).join();

        assertTrue(provider.isInitialized());
        assertEquals("u1", provider.userIdValue());
        assertEquals("s1", provider.scopeIdValue());
        assertEquals("sess1", provider.sessionIdValue());
        assertTrue(memory.registerStoreCalled);
        assertSame(scopeConfig, memory.scopeConfig);
        assertEquals("s1", memory.scopeId);
    }

    @Test
    void initializeSkipsRegisterWhenMemoryAlreadyHasKvStore() {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        memory.reportedKvStore = new InMemoryKVStore();
        OpenJiuwenMemoryProvider provider = provider(memory);

        provider.initialize(Map.of()).join();

        assertTrue(provider.isInitialized());
        assertFalse(memory.registerStoreCalled);
    }

    @Test
    void handleSearchAndSummaryReturnPythonJsonShape() throws Exception {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        memory.userResults = List.of(mem("mem-1", "likes java", MemoryType.USER_PROFILE, 0.91d));
        memory.summaryResults = List.of(mem("sum-1", "talked about tests", MemoryType.SUMMARY, 0.82d));
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);

        Map<?, ?> searchPayload = json(provider.handleToolCall(
                "ltm_search", Map.of("query", "java", "num", 2, "threshold", 0.7d)).join());
        Map<?, ?> summaryPayload = json(provider.handleToolCall(
                "ltm_search_summary", Map.of("query", "tests", "num", 4)).join());

        assertEquals(1, ((Number) searchPayload.get("count")).intValue());
        Map<?, ?> first = ((List<Map<?, ?>>) searchPayload.get("results")).get(0);
        assertEquals("mem-1", first.get("id"));
        assertEquals("likes java", first.get("content"));
        assertEquals("user_profile", first.get("type"));
        assertEquals("java", memory.searchUserMemQuery);
        assertEquals(2, memory.searchUserMemNum);
        assertEquals(0.7d, memory.searchUserMemThreshold);

        assertEquals(1, ((Number) summaryPayload.get("count")).intValue());
        assertEquals("sum-1", ((Map<?, ?>) ((List<?>) summaryPayload.get("results")).get(0)).get("id"));
        assertEquals("tests", memory.searchSummaryQuery);
        assertEquals(4, memory.searchSummaryNum);
    }

    @Test
    void handleUnknownToolAndExceptionReturnJsonErrors() throws Exception {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);

        Map<?, ?> unknownPayload = json(provider.handleToolCall("unknown", Map.of()).join());
        assertTrue(String.valueOf(unknownPayload.get("error")).contains("Unknown tool: unknown"));

        memory.searchError = new IllegalStateException("search failed");
        Map<?, ?> failedPayload = json(provider.handleToolCall("ltm_search", Map.of("query", "x")).join());
        assertEquals("search failed", failedPayload.get("error"));
        assertEquals(List.of(), failedPayload.get("results"));
    }

    @Test
    void prefetchFormatsMemoryAndSummarySections() {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        memory.userResults = List.of(mem("mem-1", "first memory", MemoryType.SEMANTIC_MEMORY, 0.915d));
        memory.summaryResults = List.of(mem("sum-1", "history summary", MemoryType.SUMMARY, 0.8d));
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);

        String rendered = provider.prefetch("java", Map.of("user_id", "runtime-u", "scope_id", "runtime-s")).join();

        assertEquals("""
                ## Related Memories
                - [semantic_memory] first memory (score: 0.92)

                ## Related History Summaries
                - history summary (score: 0.80)""", rendered);
        assertEquals("runtime-u", memory.searchUserMemUserId);
        assertEquals("runtime-s", memory.searchUserMemScopeId);
        assertEquals(5, memory.searchUserMemNum);
        assertEquals(3, memory.searchSummaryNum);
    }

    @Test
    void prefetchNotInitializedOrSearchFailuresReturnSafely() {
        assertEquals("", provider(new RecordingLongTermMemory()).prefetch("x", Map.of()).join());

        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        memory.searchError = new IllegalStateException("search failed");
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);

        assertEquals("", provider.prefetch("x", Map.of()).join());
    }

    @Test
    void syncTurnSendsOnlyNonEmptyMessages() {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);

        provider.syncTurn("hello", "", Map.of("user_id", "u2", "scope_id", "s2", "session_id", "sess2")).join();

        assertEquals(1, memory.addedMessages.size());
        assertEquals("user", memory.addedMessages.get(0).getRole());
        assertEquals("hello", memory.addedMessages.get(0).getContentAsString());
        assertEquals("u2", memory.addUserId);
        assertEquals("s2", memory.addScopeId);
        assertEquals("sess2", memory.addSessionId);

        List<BaseMessage> previous = memory.addedMessages;
        provider.syncTurn("", "", Map.of()).join();
        assertSame(previous, memory.addedMessages);

        provider.syncTurn("", "answer", Map.of()).join();
        assertNotSame(previous, memory.addedMessages);
        assertEquals("assistant", memory.addedMessages.get(0).getRole());
    }

    @Test
    void syncTurnSwallowsMemoryFailureAndShutdownResetsInitialized() {
        RecordingLongTermMemory memory = new RecordingLongTermMemory();
        OpenJiuwenMemoryProvider provider = initializedProvider(memory);
        memory.addError = new IllegalStateException("add failed");

        provider.syncTurn("u", "a", Map.of()).join();
        assertTrue(provider.isInitialized());

        provider.shutdown().join();

        assertFalse(provider.isInitialized());
    }

    private static OpenJiuwenMemoryProvider initializedProvider(RecordingLongTermMemory memory) {
        OpenJiuwenMemoryProvider provider = provider(memory);
        provider.initialize(Map.of("user_id", "init-u", "scope_id", "init-s", "session_id", "init-session")).join();
        return provider;
    }

    private static OpenJiuwenMemoryProvider provider(RecordingLongTermMemory memory) {
        return provider(memory, null);
    }

    private static OpenJiuwenMemoryProvider provider(RecordingLongTermMemory memory, MemoryScopeConfig scopeConfig) {
        return new OpenJiuwenMemoryProvider(
                Map.of(),
                new InMemoryKVStore(),
                new NoopVectorStore(),
                new DefaultDbStore<>(new Object()),
                new NoopEmbedding(),
                null,
                scopeConfig,
                new AgentMemoryConfig(),
                memory);
    }

    private static MemResult mem(String memId, String content, MemoryType type, double score) {
        return new MemResult(new MemInfo(memId, content, type, null), score);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) ((Map<String, Object>) schema.get("parameters")).get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        return (List<String>) ((Map<String, Object>) schema.get("parameters")).get("required");
    }

    private static Map<?, ?> json(String payload) throws Exception {
        return MAPPER.readValue(payload, Map.class);
    }

    /**
     * Mirrors Python's injected {@code LongTermMemory} dependency in
     * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.
     */
    private static final class RecordingLongTermMemory extends LongTermMemory {
        private BaseKVStore reportedKvStore;
        private List<MemResult> userResults = List.of();
        private List<MemResult> summaryResults = List.of();
        private RuntimeException searchError;
        private RuntimeException addError;
        private boolean registerStoreCalled;
        private String scopeId;
        private MemoryScopeConfig scopeConfig;
        private String searchUserMemQuery;
        private int searchUserMemNum;
        private double searchUserMemThreshold;
        private String searchUserMemUserId;
        private String searchUserMemScopeId;
        private String searchSummaryQuery;
        private int searchSummaryNum;
        private List<BaseMessage> addedMessages = List.of();
        private String addUserId;
        private String addScopeId;
        private String addSessionId;

        @Override
        public BaseKVStore getKvStore() {
            return reportedKvStore;
        }

        @Override
        public CompletableFuture<Void> registerStore(
                BaseKVStore kvStore,
                BaseVectorStore vectorStore,
                BaseDbStore<?> dbStore,
                Embedding embeddingModel) {
            registerStoreCalled = true;
            reportedKvStore = kvStore;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig) {
            this.scopeId = scopeId;
            this.scopeConfig = memoryScopeConfig;
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<List<MemResult>> searchUserMem(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold) {
            if (searchError != null) {
                return CompletableFuture.failedFuture(searchError);
            }
            searchUserMemQuery = query;
            searchUserMemNum = num;
            searchUserMemThreshold = threshold;
            searchUserMemUserId = userId;
            searchUserMemScopeId = scopeId;
            return CompletableFuture.completedFuture(userResults);
        }

        @Override
        public CompletableFuture<List<MemResult>> searchUserHistorySummary(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold) {
            if (searchError != null) {
                return CompletableFuture.failedFuture(searchError);
            }
            searchSummaryQuery = query;
            searchSummaryNum = num;
            return CompletableFuture.completedFuture(summaryResults);
        }

        @Override
        public CompletableFuture<AddMemResult> addMessages(
                List<BaseMessage> messages,
                AgentMemoryConfig agentConfig,
                String userId,
                String scopeId,
                String sessionId) {
            if (addError != null) {
                return CompletableFuture.failedFuture(addError);
            }
            addedMessages = List.copyOf(messages);
            addUserId = userId;
            addScopeId = scopeId;
            addSessionId = sessionId;
            return CompletableFuture.completedFuture(new AddMemResult());
        }
    }

    /**
     * Mirrors Python's injected {@code BaseVectorStore} dependency in
     * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.
     */
    private static final class NoopVectorStore extends BaseVectorStore {
        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addDocs(
                String collectionName,
                List<Map<String, Object>> docs,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(
                String collectionName,
                List<Double> queryVector,
                String vectorField,
                int topK,
                Map<String, Object> filters,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(
                String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }
    }

    /**
     * Mirrors Python's injected {@code Embedding} dependency in
     * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.
     */
    private static final class NoopEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(texts.stream().map(ignored -> List.of(0.0d)).toList());
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }
}
