/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.spi.store.BaseKVStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenJiuwenMemoryProvider.
 * <p>
 * Mirrors Python's test_openjiuwen_memory_provider.py from
 * <code>tests/unit_tests/core/memory/external/test_openjiuwen_memory_provider.py</code>.
 */
@DisplayName("OpenJiuwen Memory Provider Tests")
class TestOpenJiuwenMemoryProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    class TestNameAndAvailability {
        @Test
        void testNameReturnsOpenjiuwen() {
            assertEquals("openjiuwen", providerWithStores(new FakeLtm()).name());
        }

        @Test
        void testIsAvailableWithAllStores() {
            assertTrue(providerWithStores(new FakeLtm()).isAvailable());
        }

        @Test
        void testIsAvailableWithEmbeddingConfig() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(
                    Map.of("embedding", Map.of("model_name", "text-embedding-ada-002")));
            assertTrue(provider.isAvailable());
        }

        @Test
        void testIsAvailableWithNoStoresNoConfig() {
            assertFalse(new OpenJiuwenMemoryProvider().isAvailable());
        }

        @Test
        void testIsAvailableWithEmptyConfig() {
            assertFalse(new OpenJiuwenMemoryProvider(Map.of()).isAvailable());
        }

        @Test
        void testIsAvailableWithPartialStores() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(
                    Map.of(), new InMemoryKVStore(), vectorStore(), null, null);
            assertFalse(provider.isAvailable());
        }
    }

    @Nested
    class TestIsInitialized {
        @Test
        void testNotInitializedByDefault() {
            assertFalse(providerWithStores(new FakeLtm()).isInitialized());
        }

        @Test
        void testInitializedAfterInitialize() {
            OpenJiuwenMemoryProvider provider = providerWithStores(new FakeLtm());

            provider.initialize(Map.of("user_id", "u1", "scope_id", "s1")).join();

            assertTrue(provider.isInitialized());
        }

        @Test
        void testShutdownResetsInitialized() {
            OpenJiuwenMemoryProvider provider = providerWithStores(new FakeLtm());
            provider.initialize(Map.of()).join();

            provider.shutdown().join();

            assertFalse(provider.isInitialized());
        }
    }

    @Nested
    class TestInitialize {
        @Test
        void testInitializeWithPreProvidedStores() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = providerWithStores(ltm);

            provider.initialize(Map.of("user_id", "u1", "scope_id", "s1", "session_id", "sess1")).join();
            provider.prefetch("probe", Map.of()).join();

            assertEquals(1, ltm.registerStoreCalls);
            assertEquals("u1", ltm.lastUserMemUserId);
            assertEquals("s1", ltm.lastUserMemScopeId);
        }

        @Test
        void testInitializeSkipsRegisterIfLtmAlreadyHasKv() {
            FakeLtm ltm = new FakeLtm();
            ltm.kvStore = new InMemoryKVStore();
            OpenJiuwenMemoryProvider provider = providerWithStores(ltm);

            provider.initialize(Map.of()).join();

            assertEquals(0, ltm.registerStoreCalls);
        }

        @Test
        void testInitializeSetsScopeConfigWhenNonDefault() {
            FakeLtm ltm = new FakeLtm();
            MemoryScopeConfig scopeConfig = new MemoryScopeConfig();
            OpenJiuwenMemoryProvider provider = providerWithStores(ltm, scopeConfig);

            provider.initialize(Map.of("scope_id", "my_scope")).join();

            assertSame(scopeConfig, ltm.lastScopeConfig);
            assertEquals("my_scope", ltm.lastScopeId);
        }

        @Test
        void testInitializeSkipsScopeConfigForDefaultScope() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = providerWithStores(ltm, null);

            provider.initialize(Map.of("scope_id", "__default__")).join();

            assertNull(ltm.lastScopeConfig);
        }

        @Test
        void testInitializeCreatesStoresFromConfig() {
            FakeLtm ltm = new FakeLtm();
            CountingProvider provider = new CountingProvider(
                    Map.of("embedding", Map.of("model_name", "text-embedding-ada-002")), ltm, false);

            provider.initialize(Map.of()).join();

            assertEquals(1, provider.createKvStoreCalls);
            assertEquals(1, provider.createVectorStoreCalls);
            assertEquals(1, provider.createDbStoreCalls);
            assertEquals(1, provider.createEmbeddingCalls);
            assertTrue(provider.isInitialized());
        }

        @Test
        void testInitializeFailsIfStoreCreationReturnsNone() {
            CountingProvider provider = new CountingProvider(Map.of(), new FakeLtm(), true);

            provider.initialize(Map.of()).join();

            assertFalse(provider.isInitialized());
        }
    }

    @Nested
    class TestSystemPromptBlock {
        @Test
        void testReturnsNonEmptyString() {
            String prompt = providerWithStores(new FakeLtm()).systemPromptBlock();

            assertFalse(prompt.isEmpty());
            assertTrue(prompt.contains("ltm_search"));
        }
    }

    @Nested
    class TestGetToolSchemas {
        @Test
        void testReturnsTwoSchemas() {
            List<Map<String, Object>> schemas = providerWithStores(new FakeLtm()).getToolSchemas();

            assertEquals(2, schemas.size());
            assertEquals(List.of("ltm_search", "ltm_search_summary"),
                    schemas.stream().map(schema -> schema.get("name")).toList());
        }

        @Test
        void testLtmSearchSchemaStructure() {
            Map<String, Object> schema = OpenJiuwenMemoryProvider.LTM_SEARCH_SCHEMA;

            assertEquals("ltm_search", schema.get("name"));
            assertTrue(properties(schema).containsKey("query"));
            assertEquals(List.of("query"), required(schema));
        }

        @Test
        void testLtmSearchSummarySchemaStructure() {
            Map<String, Object> schema = OpenJiuwenMemoryProvider.LTM_SEARCH_SUMMARY_SCHEMA;

            assertEquals("ltm_search_summary", schema.get("name"));
            assertTrue(properties(schema).containsKey("query"));
            assertEquals(List.of("query"), required(schema));
        }
    }

    @Nested
    class TestHandleToolCall {
        @Test
        void testNotInitializedReturnsError() throws Exception {
            Map<String, Object> parsed = json(new OpenJiuwenMemoryProvider()
                    .handleToolCall("ltm_search", Map.of("query", "test")).join());

            assertTrue(parsed.containsKey("error"));
        }

        @Test
        void testUnknownToolReturnsError() throws Exception {
            OpenJiuwenMemoryProvider provider = initializedProvider(new FakeLtm());

            Map<String, Object> parsed = json(provider.handleToolCall("unknown_tool", Map.of()).join());

            assertTrue(parsed.containsKey("error"));
            assertTrue(String.valueOf(parsed.get("error")).contains("unknown_tool"));
        }

        @Test
        void testLtmSearchReturnsResults() throws Exception {
            FakeLtm ltm = new FakeLtm();
            ltm.userMemResults = List.of(mem("m1", "likes Python", MemoryType.FRAGMENT_MEMORY, 0.92));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            Map<String, Object> parsed = json(provider.handleToolCall("ltm_search", Map.of("query", "likes what")).join());

            assertEquals(1, parsed.get("count"));
            Map<String, Object> first = firstResult(parsed);
            assertEquals("m1", first.get("id"));
            assertEquals("likes Python", first.get("content"));
            assertEquals("fragment", first.get("type"));
            assertEquals(0.92, (Double) first.get("score"), 0.0001);
        }

        @Test
        void testLtmSearchSummaryReturnsResults() throws Exception {
            FakeLtm ltm = new FakeLtm();
            ltm.summaryResults = List.of(mem("s1", "discussed Rust vs Java", MemoryType.SUMMARY, 0.78));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            Map<String, Object> parsed = json(provider.handleToolCall(
                    "ltm_search_summary", Map.of("query", "Rust")).join());

            assertEquals(1, parsed.get("count"));
            assertEquals("discussed Rust vs Java", firstResult(parsed).get("content"));
        }

        @Test
        void testLtmSearchEmptyResults() throws Exception {
            OpenJiuwenMemoryProvider provider = initializedProvider(new FakeLtm());

            Map<String, Object> parsed = json(provider.handleToolCall("ltm_search", Map.of("query", "nothing")).join());

            assertEquals(0, parsed.get("count"));
            assertEquals(List.of(), parsed.get("results"));
        }

        @Test
        void testHandleToolCallExceptionReturnsError() throws Exception {
            FakeLtm ltm = new FakeLtm();
            ltm.searchUserMemException = new RuntimeException("db down");
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            Map<String, Object> parsed = json(provider.handleToolCall("ltm_search", Map.of("query", "test")).join());

            assertTrue(parsed.containsKey("error"));
            assertTrue(String.valueOf(parsed.get("error")).contains("db down"));
        }
    }

    @Nested
    class TestPrefetch {
        @Test
        void testNotInitializedReturnsEmpty() {
            assertEquals("", new OpenJiuwenMemoryProvider().prefetch("test query", Map.of()).join());
        }

        @Test
        void testPrefetchWithMemResults() {
            FakeLtm ltm = new FakeLtm();
            ltm.userMemResults = List.of(mem("id1", "likes Rust", MemoryType.FRAGMENT_MEMORY, 0.88));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            String result = provider.prefetch("Rust", Map.of("user_id", "u1", "scope_id", "s1")).join();

            assertTrue(result.contains("## Related Memories"));
            assertTrue(result.contains("likes Rust"));
            assertTrue(result.contains("fragment"));
            assertEquals("Rust", ltm.lastUserMemQuery);
            assertEquals(5, ltm.lastUserMemNum);
            assertEquals("u1", ltm.lastUserMemUserId);
            assertEquals("s1", ltm.lastUserMemScopeId);
            assertEquals(0.3, ltm.lastUserMemThreshold, 0.0001);
        }

        @Test
        void testPrefetchWithSummaryResults() {
            FakeLtm ltm = new FakeLtm();
            ltm.summaryResults = List.of(mem("id1", "discussed ownership", MemoryType.SUMMARY, 0.75));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            String result = provider.prefetch("ownership", Map.of()).join();

            assertTrue(result.contains("## Related History Summaries"));
            assertTrue(result.contains("discussed ownership"));
        }

        @Test
        void testPrefetchWithBothResults() {
            FakeLtm ltm = new FakeLtm();
            ltm.userMemResults = List.of(mem("id1", "likes Rust", MemoryType.FRAGMENT_MEMORY, 0.9));
            ltm.summaryResults = List.of(mem("id2", "Rust summary", MemoryType.SUMMARY, 0.7));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            String result = provider.prefetch("Rust", Map.of()).join();

            assertTrue(result.contains("## Related Memories"));
            assertTrue(result.contains("## Related History Summaries"));
        }

        @Test
        void testPrefetchNoResultsReturnsEmpty() {
            OpenJiuwenMemoryProvider provider = initializedProvider(new FakeLtm());

            assertEquals("", provider.prefetch("nothing", Map.of()).join());
        }

        @Test
        void testPrefetchSearchExceptionReturnsPartial() {
            FakeLtm ltm = new FakeLtm();
            ltm.searchUserMemException = new RuntimeException("search error");
            ltm.summaryResults = List.of(mem("id1", "fallback summary", MemoryType.SUMMARY, 0.6));
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            String result = provider.prefetch("test", Map.of()).join();

            assertTrue(result.contains("fallback summary"));
            assertFalse(result.contains("## Related Memories"));
        }

        @Test
        void testPrefetchUsesDefaultUserScope() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.prefetch("test", Map.of()).join();

            assertEquals("__default__", ltm.lastUserMemUserId);
            assertEquals("__default__", ltm.lastUserMemScopeId);
        }
    }

    @Nested
    class TestSyncTurn {
        @Test
        void testNotInitializedDoesNothing() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = providerWithStores(ltm);

            provider.syncTurn("hello", "hi", Map.of()).join();

            assertEquals(0, ltm.addMessagesCalls);
        }

        @Test
        void testSyncTurnWithBothMessages() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.syncTurn("hello", "hi there",
                    Map.of("user_id", "u1", "scope_id", "s1", "session_id", "sess1")).join();

            assertEquals(1, ltm.addMessagesCalls);
            assertEquals(2, ltm.lastMessages.size());
            assertEquals("u1", ltm.lastAddUserId);
            assertEquals("s1", ltm.lastAddScopeId);
            assertEquals("sess1", ltm.lastAddSessionId);
        }

        @Test
        void testSyncTurnWithOnlyUserMsg() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.syncTurn("hello", "", Map.of()).join();

            assertEquals(1, ltm.lastMessages.size());
            assertEquals("user", ltm.lastMessages.get(0).getRole());
        }

        @Test
        void testSyncTurnWithOnlyAssistantMsg() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.syncTurn("", "hi there", Map.of()).join();

            assertEquals(1, ltm.lastMessages.size());
            assertEquals("assistant", ltm.lastMessages.get(0).getRole());
        }

        @Test
        void testSyncTurnWithEmptyMessagesDoesNothing() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.syncTurn("", "", Map.of()).join();

            assertEquals(0, ltm.addMessagesCalls);
        }

        @Test
        void testSyncTurnExceptionIsSwallowed() {
            FakeLtm ltm = new FakeLtm();
            ltm.addMessagesException = new RuntimeException("write failed");
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            assertDoesNotThrow(() -> provider.syncTurn("hello", "hi", Map.of()).join());
        }

        @Test
        void testSyncTurnUsesDefaultIds() {
            FakeLtm ltm = new FakeLtm();
            OpenJiuwenMemoryProvider provider = initializedProvider(ltm);

            provider.syncTurn("hello", "hi", Map.of()).join();

            assertEquals("__default__", ltm.lastAddUserId);
            assertEquals("__default__", ltm.lastAddScopeId);
            assertEquals("__default__", ltm.lastAddSessionId);
        }
    }

    @Nested
    class TestShutdown {
        @Test
        void testShutdownResetsInitialized() {
            OpenJiuwenMemoryProvider provider = initializedProvider(new FakeLtm());
            assertTrue(provider.isInitialized());

            provider.shutdown().join();

            assertFalse(provider.isInitialized());
        }
    }

    private static OpenJiuwenMemoryProvider initializedProvider(FakeLtm ltm) {
        OpenJiuwenMemoryProvider provider = providerWithStores(ltm);
        provider.initialize(Map.of()).join();
        return provider;
    }

    private static OpenJiuwenMemoryProvider providerWithStores(FakeLtm ltm) {
        return providerWithStores(ltm, null);
    }

    private static OpenJiuwenMemoryProvider providerWithStores(FakeLtm ltm, MemoryScopeConfig scopeConfig) {
        return new OpenJiuwenMemoryProvider(Map.of(), new InMemoryKVStore(), vectorStore(), dbStore(), new HashEmbedding(),
                scopeConfig, new AgentMemoryConfig(), () -> ltm);
    }

    private static VectorStore vectorStore() {
        return new InMemoryVectorStore(new VectorStoreConfig("chroma", "openjiuwen_memory_test"), "hybrid");
    }

    private static BaseDbStore<?> dbStore() {
        return new DefaultDbStore("jdbc:h2:mem:openjiuwen_provider_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }

    private static MemResult mem(String id, String content, MemoryType type, double score) {
        return MemResult.builder()
                .memInfo(MemInfo.builder().memId(id).content(content).type(type).build())
                .score(score)
                .build();
    }

    private static Map<String, Object> json(String payload) throws Exception {
        return MAPPER.readValue(payload, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstResult(Map<String, Object> parsed) {
        return ((List<Map<String, Object>>) parsed.get("results")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        Map<String, Object> parameters = (Map<String, Object>) schema.get("parameters");
        return (Map<String, Object>) parameters.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        Map<String, Object> parameters = (Map<String, Object>) schema.get("parameters");
        return (List<String>) parameters.get("required");
    }

    static class FakeLtm implements OpenJiuwenMemoryProvider.LongTermMemoryClient {
        BaseKVStore kvStore;
        int registerStoreCalls;
        int setScopeConfigCalls;
        String lastScopeId;
        MemoryScopeConfig lastScopeConfig;
        List<MemResult> userMemResults = List.of();
        List<MemResult> summaryResults = List.of();
        RuntimeException searchUserMemException;
        RuntimeException summaryException;
        RuntimeException addMessagesException;
        String lastUserMemQuery;
        int lastUserMemNum;
        String lastUserMemUserId;
        String lastUserMemScopeId;
        double lastUserMemThreshold;
        int addMessagesCalls;
        List<BaseMessage> lastMessages = List.of();
        String lastAddUserId;
        String lastAddScopeId;
        String lastAddSessionId;

        @Override
        public BaseKVStore getKvStore() {
            return kvStore;
        }

        @Override
        public void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embedding) {
            registerStoreCalls++;
            this.kvStore = kvStore;
        }

        @Override
        public boolean setScopeConfig(String scopeId, MemoryScopeConfig scopeConfig) {
            setScopeConfigCalls++;
            lastScopeId = scopeId;
            lastScopeConfig = scopeConfig;
            return true;
        }

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            if (searchUserMemException != null) {
                throw searchUserMemException;
            }
            lastUserMemQuery = query;
            lastUserMemNum = num;
            lastUserMemUserId = userId;
            lastUserMemScopeId = scopeId;
            lastUserMemThreshold = threshold;
            return userMemResults;
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId,
                                                        String scopeId, double threshold) {
            if (summaryException != null) {
                throw summaryException;
            }
            return summaryResults;
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config,
                                String userId, String scopeId, String sessionId) {
            if (addMessagesException != null) {
                throw addMessagesException;
            }
            addMessagesCalls++;
            lastMessages = new ArrayList<>(messages);
            lastAddUserId = userId;
            lastAddScopeId = scopeId;
            lastAddSessionId = sessionId;
        }
    }

    static class CountingProvider extends OpenJiuwenMemoryProvider {
        final boolean failKvCreation;
        int createKvStoreCalls;
        int createVectorStoreCalls;
        int createDbStoreCalls;
        int createEmbeddingCalls;

        CountingProvider(Map<String, Object> config, FakeLtm ltm, boolean failKvCreation) {
            super(config, null, null, null, null, null, new AgentMemoryConfig(), () -> ltm);
            this.failKvCreation = failKvCreation;
        }

        @Override
        protected BaseKVStore createKvStore() {
            createKvStoreCalls++;
            return failKvCreation ? null : new InMemoryKVStore();
        }

        @Override
        protected VectorStore createVectorStore() {
            createVectorStoreCalls++;
            return vectorStore();
        }

        @Override
        protected BaseDbStore<?> createDbStore() {
            createDbStoreCalls++;
            return dbStore();
        }

        @Override
        protected Embedding createEmbedding() {
            createEmbeddingCalls++;
            return new HashEmbedding();
        }
    }
}
