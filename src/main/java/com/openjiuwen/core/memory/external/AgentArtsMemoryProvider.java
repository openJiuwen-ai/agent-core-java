/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * AgentArts provider with backend-neutral prompt and tool surface.
 *
 * <p>Mirrors Python's {@code AgentArtsMemoryProvider} in
 * {@code openjiuwen/core/memory/external/agentarts_memory_provider.py}.</p>
 *
 * <p>Java adaptation note: the Python SDK boundary is represented by a small client
 * seam so tests and future SDK integration can share the same provider behavior.</p>
 */
public class AgentArtsMemoryProvider extends MemoryProvider {

    static final String DEFAULT_BASE_URL = "https://memory.cn-southwest-2.huaweicloud-agentarts.com";
    static final String AGENTARTS_EXTRA = "agentarts";
    static final String AGENTARTS_PACKAGE = "agentarts-sdk";
    static final int MAX_TOP_K = 100;
    static final int DEFAULT_TOP_K = 10;
    static final double DEFAULT_MIN_SCORE = 0.5;
    static final String SESSION_MAPPING_KEY_PREFIX = "agentarts/session_mapping";
    static final List<String> STRATEGY_TYPES = List.of(
            "semantic", "summary", "user_preference", "episodic", "event", "custom");
    static final Map<String, Object> EXTERNAL_MEMORY_SEARCH_SCHEMA = createExternalMemorySearchSchema();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String baseUrl;
    private String apiKey;
    private String spaceId;
    private String defaultActorId;
    private String actorId;
    private String defaultAssistantId;
    private String assistantId;
    private AgentArtsClient client;
    private String sessionId;
    private final BaseKVStore sessionMappingStore;
    private final ClientFactory clientFactory;
    private boolean initialized;
    private int consecutiveFailures;

    interface ClientFactory {
        AgentArtsClient create(String apiKey);
    }

    interface AgentArtsClient {
        List<SearchEntry> searchMemories(String spaceId, MemorySearchFilterPayload filters) throws Exception;

        String createMemorySession(Map<String, Object> payload) throws Exception;

        void addMessages(String spaceId, String sessionId, List<TextMessagePayload> messages) throws Exception;

        default void configureBaseUrl(String baseUrl) {
        }
    }

    record SearchEntry(String memory, double score) {
    }

    record MemorySearchFilterPayload(
            String query,
            int topK,
            Double minScore,
            String strategyType,
            String actorId) {
    }

    record TextMessagePayload(
            String role,
            String content,
            String actorId,
            String assistantId) {
    }

    public AgentArtsMemoryProvider() {
        this(DEFAULT_BASE_URL, "", "", null, null, null, null, null);
    }

    public AgentArtsMemoryProvider(String apiKey, String spaceId) {
        this(DEFAULT_BASE_URL, apiKey, spaceId, null, null, null, null, null);
    }

    public AgentArtsMemoryProvider(
            String baseUrl,
            String apiKey,
            String spaceId) {
        this(baseUrl, apiKey, spaceId, null, null, null, null, null);
    }

    AgentArtsMemoryProvider(
            String baseUrl,
            String apiKey,
            String spaceId,
            String actorId,
            String assistantId,
            BaseKVStore sessionMappingStore,
            AgentArtsClient client,
            ClientFactory clientFactory) {
        this.baseUrl = trimTrailingSlash(stringOrDefault(baseUrl, DEFAULT_BASE_URL));
        this.apiKey = stringOrDefault(apiKey, "");
        this.spaceId = stringOrDefault(spaceId, "");
        this.defaultActorId = normalizeNullable(actorId);
        this.actorId = this.defaultActorId;
        this.defaultAssistantId = normalizeNullable(assistantId);
        this.assistantId = this.defaultAssistantId;
        this.client = client;
        this.sessionId = "";
        this.sessionMappingStore = sessionMappingStore == null ? new InMemoryKVStore() : sessionMappingStore;
        this.clientFactory = clientFactory == null
                ? ignored -> {
                    throw new RuntimeException(
                            "AgentArts SDK is unavailable. Install with `pip install openjiuwen[agentarts]` "
                                    + "or install `agentarts-sdk`.");
                }
                : clientFactory;
        this.initialized = false;
        this.consecutiveFailures = 0;
    }

    @Override
    public String getName() {
        return "agentarts";
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty() && !spaceId.isEmpty();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.runAsync(() -> {
            Loggers.MEMORY.info("[AgentArtsMemoryProvider] initializing with params: {}", toJsonSafe(safeKwargs));

            actorId = normalizeNullable(safeKwargs.get("user_id"));
            assistantId = normalizeNullable(firstNonBlank(
                    safeKwargs.get("assistant_id"),
                    safeKwargs.get("scope_id")));

            String runtimeSessionId = normalizeNullable(safeKwargs.get("session_id"));
            ensureMemorySession(runtimeSessionId, actorId, assistantId);
            sessionId = runtimeSessionId == null ? "" : runtimeSessionId;
            initialized = true;
        });
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(EXTERNAL_MEMORY_SEARCH_SCHEMA);
    }

    @Override
    public String systemPromptBlock() {
        return "# External Memory\n"
                + "Use `external_memory_search` to retrieve durable facts, user preferences, "
                + "and prior conversation context from long-term external memory.";
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        if (query == null || query.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<SearchEntry> items = search(query, safeKwargs);
                consecutiveFailures = 0;
                if (items.isEmpty()) {
                    return "";
                }
                List<String> lines = new ArrayList<>();
                lines.add("## External Memory");
                for (SearchEntry item : items) {
                    lines.add("- " + item.memory());
                }
                return String.join("\n", lines);
            } catch (Exception exception) {
                consecutiveFailures++;
                Loggers.MEMORY.debug("[AgentArtsMemoryProvider] prefetch failed: {}", exception.getMessage());
                return "";
            }
        });
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        if (!"external_memory_search".equals(toolName)) {
            return CompletableFuture.completedFuture(toJson(Map.of("error", "Unknown tool: " + toolName)));
        }
        String query = stringOrDefault(safeArgs.get("query"), "");
        if (query.isEmpty()) {
            return CompletableFuture.completedFuture(toJson(Map.of("error", "Missing required parameter: query")));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<SearchEntry> items = search(query, safeArgs);
                consecutiveFailures = 0;
                if (items.isEmpty()) {
                    return toJson(Map.of("result", "No relevant memories found.", "count", 0));
                }
                List<Map<String, Object>> payload = new ArrayList<>();
                for (SearchEntry item : items) {
                    payload.add(Map.of("memory", item.memory(), "score", item.score()));
                }
                return toJson(Map.of("results", payload, "count", payload.size()));
            } catch (Exception exception) {
                Loggers.MEMORY.warning("[AgentArtsMemoryProvider] failed to search relevant memories: {}",
                        exception.getMessage());
                consecutiveFailures++;
                return toJson(Map.of("error", stringOrDefault(exception.getMessage(), exception.toString())));
            }
        });
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        if (userMsg == null || userMsg.isEmpty() || assistantMsg == null || assistantMsg.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.runAsync(() -> {
            Loggers.MEMORY.info("[AgentArtsMemoryProvider] sync_turn with params: {}", toJsonSafe(safeKwargs));
            try {
                String runtimeActorId = runtimeActorId(safeKwargs);
                String runtimeAssistantId = runtimeAssistantId(safeKwargs);
                String memorySessionId = ensureMemorySession(
                        normalizeNullable(safeKwargs.get("session_id")),
                        runtimeActorId,
                        runtimeAssistantId);
                getClient().addMessages(
                        spaceId,
                        memorySessionId,
                        List.of(
                                textMessage("user", userMsg, runtimeActorId, runtimeAssistantId),
                                textMessage("assistant", assistantMsg, runtimeActorId, runtimeAssistantId)));
                consecutiveFailures = 0;
            } catch (Exception exception) {
                consecutiveFailures++;
                Loggers.MEMORY.warning("AgentArts sync failed: {}", exception.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        client = null;
        initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    AgentArtsClient getClient() {
        if (client != null) {
            return client;
        }
        client = Objects.requireNonNull(clientFactory.create(apiKey), "AgentArts clientFactory returned null.");
        client.configureBaseUrl(baseUrl);
        return client;
    }

    String baseUrlValue() {
        return baseUrl;
    }

    String apiKeyValue() {
        return apiKey;
    }

    String spaceIdValue() {
        return spaceId;
    }

    String actorIdValue() {
        return actorId;
    }

    String assistantIdValue() {
        return assistantId;
    }

    String sessionIdValue() {
        return sessionId;
    }

    int consecutiveFailuresValue() {
        return consecutiveFailures;
    }

    AgentArtsClient rawClientForTest() {
        return client;
    }

    void setClientForTest(AgentArtsClient replacement) {
        this.client = replacement;
    }

    private List<SearchEntry> search(String query, Map<String, Object> args) throws Exception {
        MemorySearchFilterPayload filters = memorySearchFilter(query, args);
        Loggers.MEMORY.info(
                "[AgentArtsMemoryProvider] search agentarts memory space [{}] with filters: {}",
                spaceId,
                toJsonSafe(filters));
        List<SearchEntry> results = getClient().searchMemories(spaceId, filters);
        List<SearchEntry> normalized = new ArrayList<>();
        for (SearchEntry result : results == null ? List.<SearchEntry>of() : results) {
            if (result != null && result.memory() != null && !result.memory().isEmpty()) {
                normalized.add(result);
            }
        }
        Loggers.MEMORY.info("[AgentArtsMemoryProvider] found {} relevant memory records", normalized.size());
        return normalized;
    }

    private MemorySearchFilterPayload memorySearchFilter(String query, Map<String, Object> args) {
        String runtimeActorId = runtimeActorId(args);
        Object rawTopK = args.get("top_k");
        int topK = rawTopK == null ? DEFAULT_TOP_K : Math.min(intValue(rawTopK, DEFAULT_TOP_K), MAX_TOP_K);
        Double minScore = args.get("min_score") == null ? DEFAULT_MIN_SCORE : doubleValue(args.get("min_score"));
        String strategyType = normalizeNullable(args.get("strategy_type"));
        return new MemorySearchFilterPayload(query, topK, minScore, strategyType, runtimeActorId);
    }

    private TextMessagePayload textMessage(
            String role,
            String content,
            String runtimeActorId,
            String runtimeAssistantId) {
        return new TextMessagePayload(role, content, runtimeActorId, runtimeAssistantId);
    }

    private String runtimeActorId(Map<String, Object> params) {
        String runtime = normalizeNullable(params.get("user_id"));
        if (runtime != null) {
            return runtime;
        }
        return firstNonBlank(actorId, defaultActorId);
    }

    private String runtimeAssistantId(Map<String, Object> params) {
        String runtimeAssistant = normalizeNullable(params.get("assistant_id"));
        if (runtimeAssistant != null) {
            return runtimeAssistant;
        }
        String scopeAssistant = normalizeNullable(params.get("scope_id"));
        if (scopeAssistant != null) {
            return scopeAssistant;
        }
        return firstNonBlank(assistantId, defaultAssistantId);
    }

    private String ensureMemorySession(
            String requestedSessionId,
            String runtimeActorId,
            String runtimeAssistantId) {
        String effectiveSessionId = firstNonBlank(requestedSessionId, sessionId);
        if (effectiveSessionId == null || effectiveSessionId.isEmpty()) {
            throw new RuntimeException("`session_id` is required");
        }

        String resolvedActorId = firstNonBlank(runtimeActorId, firstNonBlank(actorId, defaultActorId));
        String resolvedAssistantId = firstNonBlank(runtimeAssistantId, firstNonBlank(assistantId, defaultAssistantId));
        String mappingKey = SESSION_MAPPING_KEY_PREFIX + "/" + effectiveSessionId;
        Object existing = sessionMappingStore.get(mappingKey).join();
        String existingMapping = normalizeMemorySessionId(existing);
        if (!existingMapping.isEmpty()) {
            Loggers.MEMORY.info(
                    "[AgentArtsMemoryProvider] use exist session mapping entry: {} -> {}",
                    effectiveSessionId,
                    existingMapping);
            return existingMapping;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("space_id", spaceId);
        if (resolvedActorId != null && !resolvedActorId.isEmpty()) {
            payload.put("actor_id", resolvedActorId);
        }
        if (resolvedAssistantId != null && !resolvedAssistantId.isEmpty()) {
            payload.put("assistant_id", resolvedAssistantId);
        }
        String createdSessionId;
        try {
            createdSessionId = getClient().createMemorySession(payload);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage(), exception);
        }
        if (createdSessionId == null || createdSessionId.isEmpty()) {
            throw new RuntimeException("AgentArts create_memory_session did not return a session id");
        }
        Loggers.MEMORY.info(
                "[AgentArtsMemoryProvider] add session mapping entry: {} -> {}",
                effectiveSessionId,
                createdSessionId);
        sessionMappingStore.set(mappingKey, createdSessionId).join();
        return createdSessionId;
    }

    private static String normalizeMemorySessionId(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String toJsonSafe(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String normalizeNullable(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        return String.valueOf(value);
    }

    private static String firstNonBlank(Object first, Object second) {
        String firstText = normalizeNullable(first);
        if (firstText != null && !firstText.isEmpty()) {
            return firstText;
        }
        return normalizeNullable(second);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static Map<String, Object> createExternalMemorySearchSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "string", "description", "Memory search query."));
        properties.put("top_k", Map.of("type", "integer", "description", "Max results, default 10, max 100."));
        properties.put(
                "strategy_type",
                Map.of(
                        "type", "string",
                        "enum", STRATEGY_TYPES,
                        "description", "Optional memory strategy type filter. Acceptable values: "
                                + "semantic, summary, user_preference, episodic, event, custom."));
        properties.put(
                "min_score",
                Map.of("type", "number",
                        "description", "Optional minimum similarity score. (default: 0.5)"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "external_memory_search");
        schema.put(
                "description",
                "Search long-term external memory for durable facts, user preferences, "
                        + "and prior conversation context.");
        schema.put("parameters", parameters);
        return schema;
    }
}
