/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mem0 external memory provider with circuit-breaker support.
 * <p>
 * Mirrors Python's {@code Mem0MemoryProvider} from
 * {@code core/memory/external/mem0_provider.py}.
 * <p>
 * Java adaptation note: a small {@link Mem0Client} seam stands in for the
 * Python mem0 async client so tests and future SDK integration can share the
 * same provider logic.
 */
public class Mem0MemoryProvider extends MemoryProvider {

    static final Map<String, Object> PROFILE_SCHEMA = Map.of(
            "name", "mem0_profile",
            "description", "Retrieve all stored memories about the user.",
            "parameters", Map.of("type", "object", "properties", Map.of(), "required", List.of())
    );
    static final Map<String, Object> SEARCH_SCHEMA = Map.of(
            "name", "mem0_search",
            "description", "Search memories by meaning.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "What to search for."),
                            "rerank", Map.of("type", "boolean", "description", "Enable reranking."),
                            "top_k", Map.of("type", "integer", "description", "Max results.")
                    ),
                    "required", List.of("query")
            )
    );
    static final Map<String, Object> CONCLUDE_SCHEMA = Map.of(
            "name", "mem0_conclude",
            "description", "Store a durable fact about the user.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of("conclusion", Map.of("type", "string", "description", "The fact to store.")),
                    "required", List.of("conclusion")
            )
    );

    private static final int BREAKER_THRESHOLD = 5;
    private static final double BREAKER_COOLDOWN_SECS = 120.0;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String apiKey;
    private String userId;
    private String agentId;
    private boolean rerank;
    private Mem0Client client;
    private boolean initialized;
    private int consecutiveFailures;
    private long breakerOpenUntilMillis;
    private CompletableFuture<?> prefetchTask;

    @FunctionalInterface
    interface Mem0Call {
        Object run() throws Exception;
    }

    interface Mem0Client {
        Object search(Map<String, Object> kwargs) throws Exception;

        Object add(List<Map<String, Object>> messages, Map<String, Object> kwargs) throws Exception;

        Object getAll(Map<String, Object> kwargs) throws Exception;
    }

    public Mem0MemoryProvider(String apiKey, String baseUrl) {
        this(apiKey, "", "", false, null);
    }

    Mem0MemoryProvider(String apiKey, String userId, String agentId, boolean rerank, Mem0Client client) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.userId = userId == null ? "" : userId;
        this.agentId = agentId == null ? "" : agentId;
        this.rerank = rerank;
        this.client = client;
    }

    @Override
    public String name() {
        return "mem0";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        Map<String, Object> initKwargs = kwargs == null ? Map.of() : kwargs;
        apiKey = stringOrDefault(initKwargs.get("api_key"), apiKey);
        userId = stringOrDefault(initKwargs.get("user_id"), userId);
        agentId = stringOrDefault(initKwargs.get("agent_id"), agentId);
        if (initKwargs.containsKey("rerank")) {
            rerank = Boolean.parseBoolean(String.valueOf(initKwargs.get("rerank")));
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Mem0 API key is required. Provide api_key in provider initialization.");
        }
        initialized = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(PROFILE_SCHEMA, SEARCH_SCHEMA, CONCLUDE_SCHEMA);
    }

    @Override
    public String systemPromptBlock() {
        return "# Mem0 Memory\n"
                + "Active. User: " + userId + ".\n"
                + "Use mem0_search to find memories, mem0_conclude to store facts, "
                + "mem0_profile for a full overview.";
    }

    public CompletableFuture<Void> queuePrefetch(String query, Map<String, Object> kwargs) {
        if (query == null || query.isEmpty() || isBreakerOpen()) {
            return CompletableFuture.completedFuture(null);
        }
        prefetchTask = CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> searchArgs = new LinkedHashMap<>();
                searchArgs.put("query", query);
                searchArgs.put("filters", readFilters());
                searchArgs.put("rerank", rerank);
                searchArgs.put("top_k", Math.min(intValue(kwargs, "top_k", 5), 50));
                getClient().search(searchArgs);
                recordSuccess();
            } catch (Exception e) {
                recordFailure();
                Loggers.MEMORY.debug("Mem0 queue_prefetch failed: {}", e.getMessage());
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        if (query == null || query.isEmpty() || isBreakerOpen()) {
            return CompletableFuture.completedFuture("");
        }
        return callAsync(() -> {
            Map<String, Object> searchArgs = new LinkedHashMap<>();
            searchArgs.put("query", query);
            searchArgs.put("filters", readFilters());
            searchArgs.put("rerank", boolValue(kwargs, "rerank", rerank));
            searchArgs.put("top_k", Math.min(intValue(kwargs, "top_k", 5), 50));
            Object response = getClient().search(searchArgs);
            List<Map<String, Object>> items = unwrapResults(response);
            if (items.isEmpty()) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Object memory = item.get("memory");
                if (memory != null && !String.valueOf(memory).isEmpty()) {
                    lines.add("- " + memory);
                }
            }
            return lines.isEmpty() ? "" : "## Mem0 Memory\n" + String.join("\n", lines);
        }, "");
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        if (isBreakerOpen() || userMsg == null || userMsg.isEmpty()
                || assistantMsg == null || assistantMsg.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> messages = List.of(
                        Map.of("role", "user", "content", userMsg),
                        Map.of("role", "assistant", "content", assistantMsg)
                );
                getClient().add(messages, writeFilters());
                recordSuccess();
            } catch (Exception e) {
                recordFailure();
                Loggers.MEMORY.warn("Mem0 sync failed: {}", e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        if (isBreakerOpen()) {
            return CompletableFuture.completedFuture(toJson(Map.of(
                    "error", "Mem0 API temporarily unavailable (multiple consecutive failures). Will retry automatically."
            )));
        }
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        return callAsync(() -> {
            if ("mem0_profile".equals(toolName)) {
                Object response = getClient().getAll(Map.of("filters", readFilters()));
                List<Map<String, Object>> items = unwrapResults(response);
                if (items.isEmpty()) {
                    return toJson(Map.of("result", "No memories stored yet."));
                }
                List<String> lines = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Object memory = item.get("memory");
                    if (memory != null && !String.valueOf(memory).isEmpty()) {
                        lines.add(String.valueOf(memory));
                    }
                }
                return toJson(Map.of("result", String.join("\n", lines), "count", lines.size()));
            }

            if ("mem0_search".equals(toolName)) {
                String query = stringOrDefault(safeArgs.get("query"), "");
                if (query.isEmpty()) {
                    return toJson(Map.of("error", "Missing required parameter: query"));
                }
                Map<String, Object> searchArgs = new LinkedHashMap<>();
                searchArgs.put("query", query);
                searchArgs.put("filters", readFilters());
                searchArgs.put("rerank", boolValue(safeArgs, "rerank", false));
                searchArgs.put("top_k", Math.min(intValue(safeArgs, "top_k", 10), 50));
                List<Map<String, Object>> items = unwrapResults(getClient().search(searchArgs));
                if (items.isEmpty()) {
                    return toJson(Map.of("result", "No relevant memories found."));
                }
                List<Map<String, Object>> payload = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    payload.add(Map.of(
                            "memory", item.getOrDefault("memory", ""),
                            "score", item.getOrDefault("score", 0)
                    ));
                }
                return toJson(Map.of("results", payload, "count", payload.size()));
            }

            if ("mem0_conclude".equals(toolName)) {
                String conclusion = stringOrDefault(safeArgs.get("conclusion"), "");
                if (conclusion.isEmpty()) {
                    return toJson(Map.of("error", "Missing required parameter: conclusion"));
                }
                Map<String, Object> writeArgs = new LinkedHashMap<>(writeFilters());
                writeArgs.put("infer", false);
                getClient().add(List.of(Map.of("role", "user", "content", conclusion)), writeArgs);
                return toJson(Map.of("result", "Fact stored."));
            }

            return toJson(Map.of("error", "Unknown tool: " + toolName));
        }, toJson(Map.of("error", "Mem0 tool call failed")));
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        if (prefetchTask != null && !prefetchTask.isDone()) {
            prefetchTask.cancel(true);
        }
        prefetchTask = null;
        client = null;
        initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    void setPrefetchTaskForTest(CompletableFuture<?> task) {
        this.prefetchTask = task;
    }

    private CompletableFuture<String> callAsync(Mem0Call call, String fallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String value = String.valueOf(call.run());
                recordSuccess();
                return value;
            } catch (Exception e) {
                recordFailure();
                Loggers.MEMORY.debug("Mem0 call failed: {}", e.getMessage());
                return fallback;
            }
        });
    }

    private Mem0Client getClient() {
        if (client == null) {
            throw new IllegalStateException(
                    "Mem0 Java SDK client is unavailable. Provide a Mem0Client adapter before using the provider.");
        }
        return client;
    }

    private Map<String, Object> readFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("user_id", userId);
        if (agentId != null && !agentId.isEmpty()) {
            filters.put("agent_id", agentId);
        }
        return filters;
    }

    private Map<String, Object> writeFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("user_id", userId);
        filters.put("agent_id", agentId);
        return filters;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> unwrapResults(Object response) {
        if (response instanceof Map<?, ?> map) {
            Object results = map.get("results");
            if (results instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        out.add(toStringMap(itemMap));
                    }
                }
                return out;
            }
            return List.of();
        }
        if (response instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    out.add(toStringMap(itemMap));
                }
            }
            return out;
        }
        return List.of();
    }

    private boolean isBreakerOpen() {
        if (consecutiveFailures < BREAKER_THRESHOLD) {
            return false;
        }
        if (System.currentTimeMillis() >= breakerOpenUntilMillis) {
            consecutiveFailures = 0;
            return false;
        }
        return true;
    }

    private void recordSuccess() {
        consecutiveFailures = 0;
    }

    private void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= BREAKER_THRESHOLD) {
            breakerOpenUntilMillis = System.currentTimeMillis() + (long) (BREAKER_COOLDOWN_SECS * 1000);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> toStringMap(Map<?, ?> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        return String.valueOf(value);
    }

    private static int intValue(Map<String, Object> values, String key, int defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean boolValue(Map<String, Object> values, String key, boolean defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
