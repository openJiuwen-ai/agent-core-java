/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.memory.external.test_agentarts_memory_provider} in
 * {@code tests/unit_tests/core/memory/external/test_agentarts_memory_provider.py}.
 */
class AgentArtsMemoryProviderTest {

    @Test
    void exportedProviderImportsWithoutSdk() {
        assertEquals("AgentArtsMemoryProvider", AgentArtsMemoryProvider.class.getSimpleName());
    }

    @Test
    void externalMemoryDoesNotExportMappingAbcOrDefaultStore() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.openjiuwen.core.memory.external.MemorySessionMappingStore"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.openjiuwen.core.memory.external.InMemorySessionMappingStore"));
    }

    @Test
    void nameReturnsAgentarts() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        assertEquals("agentarts", provider.getName());
    }

    @Test
    void defaultBaseUrlAndAvailability() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        assertEquals("https://memory.cn-southwest-2.huaweicloud-agentarts.com", provider.baseUrlValue());
        assertTrue(provider.isAvailable());
    }

    @Test
    void customBaseUrlIsPreservedForSdkEndpoint() {
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                "https://custom.example.com/",
                "k",
                "space",
                null,
                null,
                null,
                new FakeAgentArtsClient(),
                null);
        assertEquals("https://custom.example.com", provider.baseUrlValue());
    }

    @Test
    void isAvailableRequiresApiKeyAndSpaceId() {
        assertFalse(provider(new FakeAgentArtsClient(), "", "space").isAvailable());
        assertFalse(provider(new FakeAgentArtsClient(), "k", "").isAvailable());
    }

    @Test
    void initializeDoesNotRecheckRequiredStaticConfig() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "", "space");

        provider.initialize(Map.of("session_id", "runtime-session")).join();

        assertFalse(provider.isAvailable());
        assertTrue(provider.isInitialized());
    }

    @Test
    void initializeCreatesUuidSessionAndKeepsStaticConfig() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                "https://configured.example.com",
                "configured-key",
                "configured-space",
                "configured-actor",
                "configured-assistant",
                null,
                fake,
                null);

        provider.initialize(Map.of(
                "user_id", "runtime-user",
                "scope_id", "runtime-scope",
                "session_id", "runtime-session",
                "base_url", "https://ignored.example.com",
                "api_key", "ignored-key",
                "space_id", "ignored-space",
                "actor_id", "ignored-actor",
                "assistant_id", "runtime-assistant")).join();

        assertEquals("https://configured.example.com", provider.baseUrlValue());
        assertEquals("configured-key", provider.apiKeyValue());
        assertEquals("configured-space", provider.spaceIdValue());
        assertEquals("runtime-user", provider.actorIdValue());
        assertEquals("runtime-assistant", provider.assistantIdValue());
        assertEquals("runtime-session", provider.sessionIdValue());
        assertEquals("runtime-user", fake.lastSessionCall.get("actor_id"));
        assertEquals("runtime-assistant", fake.lastSessionCall.get("assistant_id"));
        assertFalse(fake.lastSessionCall.containsKey("id"));
        assertThrows(NoSuchFieldException.class,
                () -> AgentArtsMemoryProvider.class.getDeclaredField("agentArtsSessionId"));
    }

    @Test
    void staticServiceConfigIsReadOnlyAfterConstruction() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                "https://configured.example.com",
                "configured-key",
                "configured-space",
                null,
                null,
                null,
                fake,
                null);

        provider.initialize(Map.of(
                "session_id", "init-session",
                "base_url", "https://ignored-init.example.com",
                "api_key", "ignored-init-key",
                "space_id", "ignored-init-space")).join();
        provider.syncTurn(
                "u-msg",
                "a-msg",
                Map.of(
                        "session_id", "call-session",
                        "base_url", "https://ignored-call.example.com",
                        "api_key", "ignored-call-key",
                        "space_id", "ignored-call-space")).join();

        assertEquals("https://configured.example.com", provider.baseUrlValue());
        assertEquals("configured-key", provider.apiKeyValue());
        assertEquals("configured-space", provider.spaceIdValue());
        assertEquals(List.of("configured-space", "configured-space"),
                fake.sessionCalls.stream().map(call -> String.valueOf(call.get("space_id"))).toList());
        assertEquals("configured-space", fake.lastMessageCall.spaceId());
    }

    @Test
    void agentartsExtraIsDeclaredInPyproject() throws Exception {
        Path pyprojectPath = Path.of("..", "agent-core-0.1.14", "pyproject.toml").normalize();
        String pyproject = Files.readString(pyprojectPath, StandardCharsets.UTF_8);
        assertTrue(pyproject.contains("agentarts = [\"agentarts-sdk>=0.1.2,<0.2\"]"));
    }

    @Test
    void getClientUsesSdkApiKeyAndConfiguresClientEndpoint() {
        RecordingClientFactory factory = new RecordingClientFactory();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                "https://custom.example.com",
                "k",
                "space",
                null,
                null,
                null,
                null,
                factory);

        AgentArtsMemoryProvider.AgentArtsClient client = provider.getClient();

        assertEquals(client, provider.getClient());
        assertEquals(List.of("k"), factory.apiKeys);
        assertEquals("https://custom.example.com", factory.client.configuredBaseUrl);
    }

    @Test
    void missingSdkErrorIsActionable() {
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider("k", "space");

        RuntimeException exception = assertThrows(RuntimeException.class, provider::getClient);

        assertTrue(exception.getMessage().contains("openjiuwen[agentarts]"));
        assertTrue(exception.getMessage().contains("agentarts-sdk"));
        assertFalse(exception.getMessage().contains(">=0.1.2"));
    }

    @Test
    void prefetchSearchesAgentartsAndFormatsBackendNeutralContext() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                "https://memory.cn-southwest-2.huaweicloud-agentarts.com",
                "k",
                "space",
                "u1",
                "a1",
                null,
                fake,
                null);
        provider.initialize(Map.of("session_id", "prefetch-session")).join();

        String result = provider.prefetch("who am I", Map.of("top_k", 3)).join();

        assertTrue(result.contains("## External Memory"));
        assertTrue(result.contains("- remember this"));
        FakeAgentArtsClient.SearchCall call = fake.searchCalls.getLast();
        assertEquals("space", call.spaceId());
        assertEquals("who am I", call.filters().query());
        assertEquals(3, call.filters().topK());
        assertEquals("u1", call.filters().actorId());
        assertFalse(result.contains("AgentArts"));
        assertFalse(result.contains("agentarts"));
    }

    @Test
    void prefetchUsesCallRuntimeScopeOverInitializedScope() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of(
                "user_id", "init-user",
                "scope_id", "init-scope",
                "session_id", "init-session")).join();

        provider.prefetch("who am I", Map.of(
                "user_id", "call-user",
                "scope_id", "call-scope",
                "session_id", "call-session")).join();

        AgentArtsMemoryProvider.MemorySearchFilterPayload filters = fake.searchCalls.getLast().filters();
        assertEquals("call-user", filters.actorId());
    }

    @Test
    void assistantIdPrecedencePrefersCallThenInitializeThenConstructorDefault() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                null,
                "default-assistant",
                null,
                fake,
                null);
        provider.initialize(Map.of("assistant_id", "init-assistant", "session_id", "init-session")).join();

        provider.prefetch("who am I", Map.of("assistant_id", "call-assistant")).join();

        assertEquals("init-assistant", fake.lastSessionCall.get("assistant_id"));

        fake = new FakeAgentArtsClient();
        provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                null,
                "default-assistant",
                null,
                fake,
                null);
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.prefetch("who am I", Map.of()).join();

        assertEquals("default-assistant", fake.lastSessionCall.get("assistant_id"));
    }

    @Test
    void prefetchWithoutResultsReturnsEmptyString() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        fake.searchResults = List.of();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "empty-result-session")).join();

        assertEquals("", provider.prefetch("missing", Map.of()).join());
    }

    @Test
    void prefetchFailureReturnsEmptyString() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        provider.initialize(Map.of("session_id", "prefetch-failure-session")).join();
        provider.setClientForTest(new FailingAgentArtsClient());

        assertEquals("", provider.prefetch("x", Map.of()).join());
    }

    @Test
    void searchToolReturnsJsonResultsAndFilters() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                "u1",
                null,
                null,
                fake,
                null);
        provider.initialize(Map.of("session_id", "search-session")).join();

        String output = provider.handleToolCall(
                "external_memory_search",
                Map.of("query", "x", "top_k", 2, "strategy_type", "semantic", "min_score", 0.7)).join();
        Map<?, ?> data = Jsons.asMap(output);

        assertEquals(1, ((Number) data.get("count")).intValue());
        Map<?, ?> result = ((List<Map<?, ?>>) data.get("results")).getFirst();
        assertEquals("remember this", result.get("memory"));
        assertEquals(0.91, ((Number) result.get("score")).doubleValue(), 0.00001);
        AgentArtsMemoryProvider.MemorySearchFilterPayload filters = fake.searchCalls.getLast().filters();
        assertEquals("x", filters.query());
        assertEquals(2, filters.topK());
        assertEquals("semantic", filters.strategyType());
        assertEquals(0.7, filters.minScore(), 0.00001);
        assertEquals("u1", filters.actorId());
    }

    @Test
    void searchToolTreatsNullTopKAsDefault() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "search-session")).join();

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "x");
        args.put("top_k", null);
        String output = provider.handleToolCall("external_memory_search", args).join();
        Map<?, ?> data = Jsons.asMap(output);

        assertEquals(1, ((Number) data.get("count")).intValue());
        assertEquals(10, fake.searchCalls.getLast().filters().topK());
    }

    @Test
    void searchToolDefaultsMinScoreToHalfWhenOmittedOrNull() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "search-session")).join();

        provider.handleToolCall("external_memory_search", Map.of("query", "x")).join();
        assertEquals(0.5, fake.searchCalls.getLast().filters().minScore(), 0.00001);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "x");
        args.put("min_score", null);
        provider.handleToolCall("external_memory_search", args).join();
        assertEquals(0.5, fake.searchCalls.getLast().filters().minScore(), 0.00001);
    }

    @Test
    void searchToolUsesCallRuntimeScopeOverInitializedScope() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of(
                "user_id", "init-user",
                "scope_id", "init-scope",
                "session_id", "init-session")).join();

        String output = provider.handleToolCall(
                "external_memory_search",
                Map.of(
                        "query", "x",
                        "user_id", "call-user",
                        "scope_id", "call-scope",
                        "session_id", "call-session")).join();
        Map<?, ?> data = Jsons.asMap(output);

        assertEquals(1, ((Number) data.get("count")).intValue());
        assertEquals("call-user", fake.searchCalls.getLast().filters().actorId());
    }

    @Test
    void searchToolRejectsUnknownToolAndMissingQuery() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        provider.initialize(Map.of("session_id", "tool-validation-session")).join();

        assertTrue(String.valueOf(Jsons.asMap(provider.handleToolCall("other", Map.of()).join()).get("error"))
                .contains("Unknown tool"));
        assertTrue(String.valueOf(Jsons.asMap(provider.handleToolCall("external_memory_search", Map.of()).join())
                .get("error")).contains("query"));
    }

    @Test
    void searchToolFailureReturnsJsonError() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        provider.initialize(Map.of("session_id", "tool-failure-session")).join();
        provider.setClientForTest(new FailingAgentArtsClient());

        Map<?, ?> data = Jsons.asMap(provider.handleToolCall("external_memory_search", Map.of("query", "x")).join());

        assertTrue(String.valueOf(data.get("error")).contains("search failed"));
    }

    @Test
    void toolSchemaAndPromptAreBackendNeutral() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");

        List<Map<String, Object>> schemas = provider.getToolSchemas();
        Map<String, Object> schema = schemas.getFirst();
        Map<String, Object> parameters = (Map<String, Object>) schema.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

        assertEquals("external_memory_search", schema.get("name"));
        assertTrue(String.valueOf(schema.get("description")).contains("external memory"));
        assertFalse(String.valueOf(schema.get("description")).contains("AgentArts"));
        assertFalse(String.valueOf(schema.get("description")).contains("agentarts"));
        assertEquals(List.of("query"), parameters.get("required"));
        assertFalse(properties.containsKey("session_id"));
        assertFalse(properties.containsKey("memory_type"));
        assertTrue(String.valueOf(((Map<?, ?>) properties.get("strategy_type")).get("description")).contains("semantic"));
        assertTrue(String.valueOf(((Map<?, ?>) properties.get("strategy_type")).get("description")).contains("custom"));
        assertEquals(
                List.of("semantic", "summary", "user_preference", "episodic", "event", "custom"),
                ((Map<?, ?>) properties.get("strategy_type")).get("enum"));

        String prompt = provider.systemPromptBlock();
        assertTrue(prompt.contains("External Memory"));
        assertTrue(prompt.contains("external_memory_search"));
        assertFalse(prompt.contains("AgentArts"));
        assertFalse(prompt.contains("agentarts"));
    }

    @Test
    void syncTurnCreatesSessionAndAppendsMessages() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                "u1",
                "a1",
                null,
                fake,
                null);
        provider.initialize(Map.of("session_id", "sync-session")).join();

        provider.syncTurn("u-msg", "a-msg", Map.of()).join();

        assertEquals("space", fake.lastSessionCall.get("space_id"));
        assertEquals("u1", fake.lastSessionCall.get("actor_id"));
        assertEquals("a1", fake.lastSessionCall.get("assistant_id"));
        assertEquals("space", fake.lastMessageCall.spaceId());
        assertEquals("server-session-1", fake.lastMessageCall.sessionId());
        List<AgentArtsMemoryProvider.TextMessagePayload> messages = fake.lastMessageCall.messages();
        assertEquals("user", messages.get(0).role());
        assertEquals("u-msg", messages.get(0).content());
        assertEquals("u1", messages.get(0).actorId());
        assertEquals("a1", messages.get(0).assistantId());
        assertEquals("assistant", messages.get(1).role());
        assertEquals("a-msg", messages.get(1).content());
    }

    @Test
    void syncTurnUsesCallerSessionId() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.syncTurn("u-msg", "a-msg", Map.of("session_id", "session-1")).join();

        assertFalse(fake.lastSessionCall.containsKey("id"));
        assertEquals("server-session-2", fake.lastMessageCall.sessionId());
    }

    @Test
    void syncTurnReusesMemorySessionMappingStoreEntry() {
        InMemoryKVStore mappingStore = new InMemoryKVStore();
        mappingStore.set("agentarts/session_mapping/session-1", "existing-memory-session").join();
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                null,
                null,
                mappingStore,
                fake,
                null);
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.syncTurn("u-msg", "a-msg", Map.of("session_id", "session-1")).join();

        assertEquals(1, fake.sessionCalls.size());
        assertEquals("existing-memory-session", fake.lastMessageCall.sessionId());
    }

    @Test
    void syncTurnRecordsServerAssignedMemorySessionMapping() {
        InMemoryKVStore mappingStore = new InMemoryKVStore();
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                null,
                null,
                mappingStore,
                fake,
                null);
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.syncTurn("u-msg", "a-msg", Map.of("session_id", "session-1")).join();
        AgentArtsMemoryProvider.TextMessagePayload firstAssistant = fake.messageCalls.getLast().messages().get(1);
        provider.syncTurn("u-msg-2", "a-msg-2", Map.of("session_id", "session-1")).join();
        Object storedSessionId = mappingStore.get("agentarts/session_mapping/session-1").join();

        assertEquals(Map.of("space_id", "space"), fake.sessionCalls.getLast());
        assertEquals(2, fake.sessionCalls.size());
        assertEquals("server-session-2", storedSessionId);
        assertEquals("a-msg", firstAssistant.content());
        assertEquals("server-session-2", fake.messageCalls.get(fake.messageCalls.size() - 2).sessionId());
        assertEquals("server-session-2", fake.messageCalls.getLast().sessionId());
    }

    @Test
    void syncTurnDecodesBytesMemorySessionMapping() {
        InMemoryKVStore mappingStore = new InMemoryKVStore();
        mappingStore.set("agentarts/session_mapping/session-1",
                "existing-memory-session".getBytes(StandardCharsets.UTF_8)).join();
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                "k",
                "space",
                null,
                null,
                mappingStore,
                fake,
                null);
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.syncTurn("u-msg", "a-msg", Map.of("session_id", "session-1")).join();

        assertEquals(1, fake.sessionCalls.size());
        assertEquals("existing-memory-session", fake.lastMessageCall.sessionId());
    }

    @Test
    void defaultMappingStoreReusesRuntimeSession() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "init-session")).join();

        provider.syncTurn("u-msg-1", "a-msg-1", Map.of("session_id", "session-1")).join();
        provider.syncTurn("u-msg-2", "a-msg-2", Map.of("session_id", "session-1")).join();

        assertEquals(2, fake.sessionCalls.size());
        assertEquals("server-session-2", fake.messageCalls.get(fake.messageCalls.size() - 2).sessionId());
        assertEquals("server-session-2", fake.messageCalls.getLast().sessionId());
    }

    @Test
    void syncTurnCallSessionDoesNotReplaceInitializedDefaultSession() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "init-session")).join();
        String initAgentArtsSessionId = "server-session-1";

        provider.syncTurn("u-msg-1", "a-msg-1", Map.of("session_id", "call-session")).join();
        String callAgentArtsSessionId = fake.lastMessageCall.sessionId();
        provider.syncTurn("u-msg-2", "a-msg-2", Map.of()).join();

        assertFalse(callAgentArtsSessionId.equals(initAgentArtsSessionId));
        assertEquals(initAgentArtsSessionId, fake.messageCalls.getLast().sessionId());
    }

    @Test
    void syncTurnEmptyOrNoneSessionIdFallsBackToInitializedSessionId() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of("session_id", "init-session")).join();
        String initAgentArtsSessionId = "server-session-1";

        provider.syncTurn("u-msg-1", "a-msg-1", mapOf("session_id", "")).join();
        provider.syncTurn("u-msg-2", "a-msg-2", mapOf("session_id", null)).join();

        assertEquals(initAgentArtsSessionId, fake.messageCalls.get(fake.messageCalls.size() - 2).sessionId());
        assertEquals(initAgentArtsSessionId, fake.messageCalls.getLast().sessionId());
    }

    @Test
    void syncTurnUsesInitializedRuntimeIdsByDefault() {
        FakeAgentArtsClient fake = new FakeAgentArtsClient();
        AgentArtsMemoryProvider provider = provider(fake, "k", "space");
        provider.initialize(Map.of(
                "user_id", "runtime-user",
                "scope_id", "runtime-scope",
                "session_id", "session-from-init")).join();
        String agentArtsSessionId = "server-session-1";

        provider.syncTurn("u-msg", "a-msg", Map.of()).join();

        assertFalse(agentArtsSessionId.equals("session-from-init"));
        assertEquals("runtime-user", fake.lastSessionCall.get("actor_id"));
        assertEquals("runtime-scope", fake.lastSessionCall.get("assistant_id"));
        assertEquals(agentArtsSessionId, fake.lastMessageCall.sessionId());
    }

    @Test
    void syncFailureDoesNotRaise() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        provider.initialize(Map.of("session_id", "sync-failure-session")).join();
        provider.setClientForTest(new FailingAgentArtsClient());

        provider.syncTurn("u", "a", Map.of()).join();
    }

    @Test
    void shutdownResetsState() {
        AgentArtsMemoryProvider provider = provider(new FakeAgentArtsClient(), "k", "space");
        provider.initialize(Map.of("session_id", "shutdown-session")).join();
        assertTrue(provider.isInitialized());
        assertNotNull(provider.getClient());

        provider.shutdown().join();

        assertFalse(provider.isInitialized());
        assertNull(provider.rawClientForTest());
    }

    private static AgentArtsMemoryProvider provider(
            AgentArtsMemoryProvider.AgentArtsClient client,
            String apiKey,
            String spaceId) {
        return new AgentArtsMemoryProvider(
                AgentArtsMemoryProvider.DEFAULT_BASE_URL,
                apiKey,
                spaceId,
                null,
                null,
                null,
                client,
                null);
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(key, value);
        return values;
    }

    private record MessageCall(
            String spaceId,
            String sessionId,
            List<AgentArtsMemoryProvider.TextMessagePayload> messages) {
    }

    private static final class FakeAgentArtsClient implements AgentArtsMemoryProvider.AgentArtsClient {

        private final List<SearchCall> searchCalls = new ArrayList<>();
        private final List<Map<String, Object>> sessionCalls = new ArrayList<>();
        private final List<MessageCall> messageCalls = new ArrayList<>();
        private List<AgentArtsMemoryProvider.SearchEntry> searchResults =
                List.of(new AgentArtsMemoryProvider.SearchEntry("remember this", 0.91));
        private Map<String, Object> lastSessionCall;
        private MessageCall lastMessageCall;
        private int nextSession;
        private String configuredBaseUrl;

        @Override
        public List<AgentArtsMemoryProvider.SearchEntry> searchMemories(
                String spaceId,
                AgentArtsMemoryProvider.MemorySearchFilterPayload filters) {
            searchCalls.add(new SearchCall(spaceId, filters));
            return searchResults;
        }

        @Override
        public String createMemorySession(Map<String, Object> payload) {
            sessionCalls.add(new LinkedHashMap<>(payload));
            lastSessionCall = sessionCalls.getLast();
            nextSession++;
            return "server-session-" + nextSession;
        }

        @Override
        public void addMessages(
                String spaceId,
                String sessionId,
                List<AgentArtsMemoryProvider.TextMessagePayload> messages) {
            lastMessageCall = new MessageCall(spaceId, sessionId, List.copyOf(messages));
            messageCalls.add(lastMessageCall);
        }

        @Override
        public void configureBaseUrl(String baseUrl) {
            configuredBaseUrl = baseUrl;
        }

        private record SearchCall(
                String spaceId,
                AgentArtsMemoryProvider.MemorySearchFilterPayload filters) {
        }
    }

    private static final class FailingAgentArtsClient implements AgentArtsMemoryProvider.AgentArtsClient {

        @Override
        public List<AgentArtsMemoryProvider.SearchEntry> searchMemories(
                String spaceId,
                AgentArtsMemoryProvider.MemorySearchFilterPayload filters) {
            throw new RuntimeException("search failed");
        }

        @Override
        public String createMemorySession(Map<String, Object> payload) {
            throw new RuntimeException("session failed");
        }

        @Override
        public void addMessages(
                String spaceId,
                String sessionId,
                List<AgentArtsMemoryProvider.TextMessagePayload> messages) {
            throw new RuntimeException("session failed");
        }
    }

    private static final class RecordingClientFactory implements AgentArtsMemoryProvider.ClientFactory {

        private final List<String> apiKeys = new ArrayList<>();
        private final FakeAgentArtsClient client = new FakeAgentArtsClient();

        @Override
        public AgentArtsMemoryProvider.AgentArtsClient create(String apiKey) {
            apiKeys.add(apiKey);
            return client;
        }
    }

    private static final class Jsons {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private Jsons() {
        }

        private static Map<?, ?> asMap(String json) {
            try {
                return MAPPER.readValue(json, Map.class);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
