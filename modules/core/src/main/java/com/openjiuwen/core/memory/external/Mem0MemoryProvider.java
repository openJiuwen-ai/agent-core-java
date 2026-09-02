/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Mem0 memory provider aligned with the current Python provider surface.
 * <p>
 * As of 2026-05-09, the official Mem0 docs expose Python / TypeScript / CLI surfaces,
 * but do not provide a confirmed official Java SDK on Maven. This class therefore keeps
 * the Java side on a direct REST path instead of adding an unofficial dependency.
 * 
 * @since 0.1.7
 */
public class Mem0MemoryProvider implements MemoryProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Map.of.
     * 
     * @param user." user."
     * @param Map.of( Map.of(
     * @since 0.1.7
     */
    private static final Map<String, Object> PROFILE_SCHEMA =
        Map.of("name", "mem0_profile", "description", "Retrieve all stored memories about the user.", "parameters",
                Map.of("type", "object", "properties", Map.of(), "required", List.of()));

    /**
     * Map.of.
     * 
     * @param meaning." meaning."
     * @param for." for."
     * @since 0.1.7
     */
    private static final Map<String, Object> SEARCH_SCHEMA = Map.of("name", "mem0_search", "description",
            "Search memories by meaning.", "parameters",
            Map.of("type", "object", "properties",
                    Map.of("query", Map.of("type", "string", "description", "What to search for."), "rerank",
                            Map.of("type", "boolean", "description", "Enable reranking."), "top_k",
                            Map.of("type", "integer", "description", "Max results.")),
                    "required", List.of("query")));

    /**
     * Map.of.
     * 
     * @param user." user."
     * @param store." store."
     * @since 0.1.7
     */
    private static final Map<String, Object> CONCLUDE_SCHEMA =
        Map.of("name", "mem0_conclude", "description", "Store a durable fact about the user.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("conclusion", Map.of("type", "string", "description", "The fact to store.")), "required",
                        List.of("conclusion")));

    private String apiKey;
    private String userId;
    private String agentId;
    private boolean isRerankEnabled;
    private String baseUrl;
    private boolean isInitialized;
    private int consecutiveFailures;
    private long breakerOpenUntilMillis;
    private final Mem0Api api;

    /**
     * Mem0MemoryProvider.
     * 
     * @since 0.1.7
     */
    public Mem0MemoryProvider() {
        this("", "", "", false, "https://api.mem0.ai", null);
    }

    /**
     * Mem0MemoryProvider.
     * 
     * @param apiKey apiKey
     * @param userId userId
     * @param agentId agentId
     * @param isRerankEnabled isRerankEnabled
     * @since 0.1.7
     */
    public Mem0MemoryProvider(String apiKey, String userId, String agentId, boolean isRerankEnabled) {
        this(apiKey, userId, agentId, isRerankEnabled, "https://api.mem0.ai", null);
    }

    Mem0MemoryProvider(String apiKey, String userId, String agentId, boolean isRerankEnabled, String baseUrl,
            Mem0Api api) {
        this.apiKey = apiKey;
        this.userId = userId;
        this.agentId = agentId;
        this.isRerankEnabled = isRerankEnabled;
        this.baseUrl = baseUrl;
        this.api = api != null ? api : new DefaultMem0Api();
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "mem0";
    }

    /**
     * isAvailable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * initialize.
     * 
     * @param kwargs kwargs
     * @since 0.1.7
     */
    @Override
    public void initialize(Map<String, Object> kwargs) {
        if (kwargs != null) {
            if (kwargs.get("api_key") != null) {
                apiKey = String.valueOf(kwargs.get("api_key"));
            }
            if (kwargs.get("user_id") != null) {
                userId = String.valueOf(kwargs.get("user_id"));
            }
            if (kwargs.get("agent_id") != null) {
                agentId = String.valueOf(kwargs.get("agent_id"));
            }
            if (kwargs.get("rerank") != null) {
                isRerankEnabled = Boolean.parseBoolean(String.valueOf(kwargs.get("rerank")));
            }
            if (kwargs.get("base_url") != null) {
                baseUrl = String.valueOf(kwargs.get("base_url"));
            }
        }
        if (!isAvailable()) {
            throw new IllegalArgumentException("Mem0 API key is required. Provide api_key in provider initialization.");
        }
        isInitialized = true;
    }

    /**
     * getToolSchemas.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(PROFILE_SCHEMA, SEARCH_SCHEMA, CONCLUDE_SCHEMA);
    }

    /**
     * handleToolCall.
     * 
     * @param toolName toolName
     * @param args args
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public String handleToolCall(String toolName, Map<String, Object> args) throws Exception {
        if (isBreakerOpen()) {
            return MAPPER.writeValueAsString(
                    Map.of("error", "Mem0 API temporarily unavailable (multiple consecutive failures). "
                            + "Will retry automatically."));
        }
        if ("mem0_profile".equals(toolName)) {
            List<Map<String, Object>> items = api.getAllMemories(baseUrl, apiKey, readFilters());
            recordSuccess();
            String result = items.isEmpty()
                    ? "No memories stored yet."
                    : String.join("\n",
                            items.stream().map(item -> String.valueOf(item.getOrDefault("memory", ""))).toList());
            return MAPPER.writeValueAsString(Map.of("result", result, "count", items.size()));
        }
        if ("mem0_search".equals(toolName)) {
            String query = args != null && args.get("query") != null ? String.valueOf(args.get("query")) : "";
            if (query.isBlank()) {
                return MAPPER.writeValueAsString(Map.of("error", "Missing required parameter: query"));
            }
            int topK =
                args != null && args.get("top_k") != null ? Integer.parseInt(String.valueOf(args.get("top_k"))) : 10;
            topK = Math.min(topK, 50);
            boolean isRerankEnabledForRequest = args != null && args.get("rerank") != null
                    ? Boolean.parseBoolean(String.valueOf(args.get("rerank")))
                    : false;
            List<Map<String, Object>> payload =
                api.searchMemories(baseUrl, apiKey, query, readFilters(), isRerankEnabledForRequest, topK);
            recordSuccess();
            return MAPPER.writeValueAsString(Map.of("results", payload, "count", payload.size()));
        }
        if ("mem0_conclude".equals(toolName)) {
            String conclusion =
                args != null && args.get("conclusion") != null ? String.valueOf(args.get("conclusion")) : "";
            if (conclusion.isBlank()) {
                return MAPPER.writeValueAsString(Map.of("error", "Missing required parameter: conclusion"));
            }
            api.addMemories(baseUrl, apiKey, List.of(Map.of("role", "user", "content", conclusion)), writeFilters(),
                    false);
            recordSuccess();
            return MAPPER.writeValueAsString(Map.of("result", "Fact stored."));
        }
        recordFailure();
        return MAPPER.writeValueAsString(Map.of("error", "Unknown tool: " + toolName));
    }

    /**
     * prefetch.
     * 
     * @param query query
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public String prefetch(String query, Map<String, Object> kwargs) throws Exception {
        if (query == null || query.isBlank() || isBreakerOpen()) {
            return "";
        }
        int topK =
            kwargs != null && kwargs.get("top_k") != null ? Integer.parseInt(String.valueOf(kwargs.get("top_k"))) : 5;
        topK = Math.min(topK, 50);
        boolean isRerankEnabledForRequest = kwargs != null && kwargs.get("rerank") != null
                ? Boolean.parseBoolean(String.valueOf(kwargs.get("rerank")))
                : isRerankEnabled;
        List<Map<String, Object>> matched =
            api.searchMemories(baseUrl, apiKey, query, readFilters(), isRerankEnabledForRequest, topK);
        if (matched.isEmpty()) {
            recordSuccess();
            return "";
        }
        StringBuilder builder = new StringBuilder("## Mem0 Memory\n");
        for (Map<String, Object> matchedLine : matched) {
            builder.append("- ").append(String.valueOf(matchedLine.getOrDefault("memory", ""))).append("\n");
        }
        recordSuccess();
        return builder.toString().trim();
    }

    /**
     * syncTurn.
     * 
     * @param userMsg userMsg
     * @param assistantMsg assistantMsg
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) throws Exception {
        if (isBreakerOpen() || userMsg == null || userMsg.isBlank() || assistantMsg == null || assistantMsg.isBlank()) {
            return;
        }
        api.addMemories(baseUrl, apiKey, List.of(Map.of("role", "user", "content", userMsg),
                Map.of("role", "assistant", "content", assistantMsg)), writeFilters(), true);
        recordSuccess();
    }

    /**
     * systemPromptBlock.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String systemPromptBlock() {
        return "# Mem0 Memory\nActive. User: " + userId + ".\n"
                + "Use mem0_search to find memories, mem0_conclude to store facts, mem0_profile for a full overview.";
    }

    /**
     * isInitialized.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * shutdown.
     * 
     * @since 0.1.7
     */
    @Override
    public void shutdown() {
        isInitialized = false;
    }

    /**
     * isBreakerOpen.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean isBreakerOpen() {
        if (consecutiveFailures < 5) {
            return false;
        }
        if (System.currentTimeMillis() >= breakerOpenUntilMillis) {
            consecutiveFailures = 0;
            breakerOpenUntilMillis = 0L;
            return false;
        }
        return true;
    }

    /**
     * recordSuccess.
     * 
     * @since 0.1.7
     */
    private void recordSuccess() {
        consecutiveFailures = 0;
        breakerOpenUntilMillis = 0L;
    }

    /**
     * recordFailure.
     * 
     * @since 0.1.7
     */
    private void recordFailure() {
        consecutiveFailures += 1;
        if (consecutiveFailures >= 5) {
            breakerOpenUntilMillis = System.currentTimeMillis() + 120_000L;
        }
    }

    /**
     * readFilters.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> readFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (userId != null && !userId.isBlank()) {
            filters.put("user_id", userId);
        }
        if (agentId != null && !agentId.isBlank()) {
            filters.put("agent_id", agentId);
        }
        return filters;
    }

    /**
     * writeFilters.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> writeFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (userId != null && !userId.isBlank()) {
            filters.put("user_id", userId);
        }
        if (agentId != null && !agentId.isBlank()) {
            filters.put("agent_id", agentId);
        }
        return filters;
    }

    interface Mem0Api {
        /**
         * getAllMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param filters filters
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        List<Map<String, Object>> getAllMemories(String baseUrl, String apiKey, Map<String, Object> filters)
                throws Exception;

        /**
         * searchMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param query query
         * @param filters filters
         * @param isRerankEnabled isRerankEnabled
         * @param topK topK
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        List<Map<String, Object>> searchMemories(String baseUrl, String apiKey, String query,
                Map<String, Object> filters, boolean isRerankEnabled, int topK) throws Exception;

        /**
         * addMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param messages messages
         * @param scope scope
         * @param shouldInfer shouldInfer
         * @throws Exception Exception
         * @since 0.1.7
         */
        void addMemories(String baseUrl, String apiKey, List<Map<String, Object>> messages, Map<String, Object> scope,
                boolean shouldInfer) throws Exception;
    }

    private static final class DefaultMem0Api implements Mem0Api {
        private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

        /**
         * getAllMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param filters filters
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public List<Map<String, Object>> getAllMemories(String baseUrl, String apiKey, Map<String, Object> filters)
                throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("filters", filters);
            Map<String, Object> response = postJson(baseUrl, "/v3/memories/", apiKey, payload);
            Object results = response.get("results");
            if (results instanceof List<?> list) {
                List<Map<String, Object>> typed = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        typed.add(castMap(map));
                    }
                }
                return typed;
            }
            return List.of();
        }

        /**
         * searchMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param query query
         * @param filters filters
         * @param isRerankEnabled isRerankEnabled
         * @param topK topK
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public List<Map<String, Object>> searchMemories(String baseUrl, String apiKey, String query,
                Map<String, Object> filters, boolean isRerankEnabled, int topK) throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", query);
            payload.put("filters", filters);
            payload.put("rerank", isRerankEnabled);
            payload.put("top_k", topK);
            Map<String, Object> response = postJson(baseUrl, "/v3/memories/search/", apiKey, payload);
            Object results = response.get("results");
            if (results instanceof List<?> list) {
                List<Map<String, Object>> typed = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        typed.add(castMap(map));
                    }
                }
                return typed;
            }
            return List.of();
        }

        /**
         * addMemories.
         * 
         * @param baseUrl baseUrl
         * @param apiKey apiKey
         * @param messages messages
         * @param scope scope
         * @param shouldInfer shouldInfer
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public void addMemories(String baseUrl, String apiKey, List<Map<String, Object>> messages,
                Map<String, Object> scope, boolean shouldInfer) throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>(scope);
            payload.put("messages", messages);
            payload.put("infer", shouldInfer);
            postJson(baseUrl, "/v3/memories/add/", apiKey, payload);
        }

        /**
         * postJson.
         * 
         * @param baseUrl baseUrl
         * @param path path
         * @param apiKey apiKey
         * @param body body
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        private Map<String, Object> postJson(String baseUrl, String path, String apiKey, Map<String, Object> body)
                throws Exception {
            String normalizedBase =
                (baseUrl == null || baseUrl.isBlank()) ? "https://api.mem0.ai" : baseUrl.replaceAll("/+$", "");
            String requestBody = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBase + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Token " + apiKey)
                    .header("Accept", "application/json").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Mem0 request failed with status " + response.statusCode() + ": " + response.body());
            }
            return MAPPER.readValue(response.body(), Map.class);
        }

        @SuppressWarnings("unchecked")
        /**
         * castMap.
         * 
         * @param source source
         * @return the result
         * @since 0.1.7
         */
        private static Map<String, Object> castMap(Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
    }
}
