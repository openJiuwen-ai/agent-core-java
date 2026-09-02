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
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

/**
 * OpenViking memory provider aligned with the current Python provider surface.
 * <p>
 * As of 2026-05-09, OpenViking docs show Python and HTTP-client usage, but no
 * confirmed official Java SDK artifact on Maven. This class therefore keeps the
 * Java side on a direct HTTP path instead of adding an unverified dependency.
 * 
 * @since 0.1.7
 */
public class OpenVikingMemoryProvider implements MemoryProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Map.of.
     * 
     * @param "搜索查询词." "搜索查询词."
     * @since 0.1.7
     */
    private static final Map<String, Object> VIKING_SEARCH_SCHEMA =
        Map.of("name", "viking_search", "description", "在知识库中进行全域搜索.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("query", Map.of("type", "string", "description", "搜索查询词."), "mode",
                                Map.of("type", "string", "enum", List.of("auto", "fast", "deep")), "top_k",
                                Map.of("type", "integer", "description", "最大返回结果数。")),
                        "required", List.of("query")));

    /**
     * Map.of.
     * 
     * @param 上的内容." 上的内容."
     * @param URI." URI."
     * @since 0.1.7
     */
    private static final Map<String, Object> VIKING_READ_SCHEMA =
        Map.of("name", "viking_read", "description", "读取 viking:// URI 上的内容.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("uri", Map.of("type", "string", "description", "要读取的 viking:// URI."), "detail",
                                Map.of("type", "string", "enum", List.of("abstract", "overview", "full"))),
                        "required", List.of("uri")));

    /**
     * Map.of.
     * 
     * @param "stat" "stat"
     * @since 0.1.7
     */
    private static final Map<String, Object> VIKING_BROWSE_SCHEMA =
        Map.of("name", "viking_browse", "description", "浏览知识库结构.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("action", Map.of("type", "string", "enum", List.of("list", "tree", "stat")), "path",
                                Map.of("type", "string", "description", "浏览路径。")),
                        "required", List.of("action")));

    /**
     * Map.of.
     * 
     * @param "要记住的事实." "要记住的事实."
     * @since 0.1.7
     */
    private static final Map<String, Object> VIKING_REMEMBER_SCHEMA =
        Map.of("name", "viking_remember", "description", "显式存储一个事实或偏好.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("content", Map.of("type", "string", "description", "要记住的事实."), "category",
                                Map.of("type", "string", "enum",
                                        List.of("preference", "entity", "event", "case", "pattern"))),
                        "required", List.of("content")));

    /**
     * Map.of.
     * 
     * @param 或文档以供后续搜索." 或文档以供后续搜索."
     * @param 或文件路径." 或文件路径."
     * @since 0.1.7
     */
    private static final Map<String, Object> VIKING_ADD_RESOURCE_SCHEMA =
        Map.of("name", "viking_add_resource", "description", "索引一个 URL 或文档以供后续搜索.", "parameters",
                Map.of("type", "object", "properties",
                        Map.of("url", Map.of("type", "string", "description", "要索引的 URL 或文件路径."), "title",
                                Map.of("type", "string", "description", "可选标题.")),
                        "required", List.of("url")));

    private String endpoint;
    private String apiKey;
    private String account;
    private String user;
    private String agent;
    private String sessionId;
    private boolean isInitialized;
    private final VikingApi api;

    /**
     * OpenVikingMemoryProvider.
     * 
     * @since 0.1.7
     */
    public OpenVikingMemoryProvider() {
        this("", "", "default", "default", "hermes", null);
    }

    /**
     * OpenVikingMemoryProvider.
     * 
     * @param endpoint endpoint
     * @param apiKey apiKey
     * @param account account
     * @param user user
     * @param agent agent
     * @since 0.1.7
     */
    public OpenVikingMemoryProvider(String endpoint, String apiKey, String account, String user, String agent) {
        this(endpoint, apiKey, account, user, agent, null);
    }

    OpenVikingMemoryProvider(String endpoint, String apiKey, String account, String user, String agent, VikingApi api) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.account = account;
        this.user = user;
        this.agent = agent;
        this.sessionId = "";
        this.api = api != null ? api : new DefaultVikingApi();
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "openviking";
    }

    /**
     * isAvailable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isAvailable() {
        return endpoint != null && !endpoint.isBlank();
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
            if (kwargs.get("endpoint") != null) {
                endpoint = String.valueOf(kwargs.get("endpoint"));
            }
            if (kwargs.get("api_key") != null) {
                apiKey = String.valueOf(kwargs.get("api_key"));
            }
            if (kwargs.get("account") != null) {
                account = String.valueOf(kwargs.get("account"));
            }
            if (kwargs.get("user") != null) {
                user = String.valueOf(kwargs.get("user"));
            }
            if (kwargs.get("agent") != null) {
                agent = String.valueOf(kwargs.get("agent"));
            }
            if (kwargs.get("session_id") != null) {
                sessionId = String.valueOf(kwargs.get("session_id"));
            }
        }
        isInitialized = isAvailable();
    }

    /**
     * getToolSchemas.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(VIKING_SEARCH_SCHEMA, VIKING_READ_SCHEMA, VIKING_BROWSE_SCHEMA, VIKING_REMEMBER_SCHEMA,
                VIKING_ADD_RESOURCE_SCHEMA);
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
        if (!isInitialized) {
            return MAPPER.writeValueAsString(Map.of("error", "OpenViking not connected"));
        }
        if ("viking_search".equals(toolName)) {
            String query = args != null && args.get("query") != null ? String.valueOf(args.get("query")) : "";
            if (query.isBlank()) {
                return MAPPER.writeValueAsString(Map.of("error", "query is required"));
            }
            int topK =
                args != null && args.get("top_k") != null ? Integer.parseInt(String.valueOf(args.get("top_k"))) : 10;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", query);
            String mode = args != null && args.get("mode") != null ? String.valueOf(args.get("mode")) : "auto";
            if (!"auto".equals(mode)) {
                payload.put("mode", mode);
            }
            if (args != null && args.get("scope") != null) {
                payload.put("target_uri", String.valueOf(args.get("scope")));
            }
            payload.put("top_k", topK);
            List<Map<String, Object>> results = api.search(endpoint, apiKey, account, user, agent, payload);
            return MAPPER.writeValueAsString(Map.of("results", results, "total", results.size()));
        }
        if ("viking_read".equals(toolName)) {
            String uri = args != null && args.get("uri") != null ? String.valueOf(args.get("uri")) : "";
            String level = args != null && args.get("detail") != null ? String.valueOf(args.get("detail")) : "overview";
            return MAPPER.writeValueAsString(api.read(endpoint, apiKey, account, user, agent, uri, level));
        }
        if ("viking_browse".equals(toolName)) {
            String action = args != null && args.get("action") != null ? String.valueOf(args.get("action")) : "list";
            String browsePath =
                args != null && args.get("path") != null ? String.valueOf(args.get("path")) : "viking://";
            return MAPPER.writeValueAsString(api.browse(endpoint, apiKey, account, user, agent, action, browsePath));
        }
        if ("viking_remember".equals(toolName)) {
            String content = args != null && args.get("content") != null ? String.valueOf(args.get("content")) : "";
            if (content.isBlank()) {
                return MAPPER.writeValueAsString(Map.of("error", "content is required"));
            }
            String category = args != null && args.get("category") != null ? String.valueOf(args.get("category")) : "";
            String text = category.isBlank() ? "[Remember] " + content : "[Remember — " + category + "] " + content;
            api.appendSessionMessage(endpoint, apiKey, account, user, agent, sessionId, "user", text);
            return MAPPER.writeValueAsString(Map.of("status", "stored", "message",
                    "Memory recorded. Will be extracted and indexed on session commit."));
        }
        if ("viking_add_resource".equals(toolName)) {
            String url = args != null && args.get("url") != null ? String.valueOf(args.get("url")) : "";
            if (url.isBlank()) {
                return MAPPER.writeValueAsString(Map.of("error", "url is required"));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", url);
            if (args != null && args.get("reason") != null) {
                payload.put("reason", String.valueOf(args.get("reason")));
            }
            return MAPPER.writeValueAsString(api.addResource(endpoint, apiKey, account, user, agent, payload));
        }
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
        if (query == null || query.isBlank()) {
            return "";
        }
        List<Map<String, Object>> matched =
            api.search(endpoint, apiKey, account, user, agent, Map.of("query", query, "top_k", 5));
        if (matched.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("## OpenViking Context\n");
        for (Map<String, Object> match : matched) {
            String score = String.format(Locale.ROOT, "%.2f",
                    Double.parseDouble(String.valueOf(match.getOrDefault("score", 0.0))));
            builder.append("- [").append(score).append("] ")
                    .append(match.getOrDefault("abstract", match.getOrDefault("content", ""))).append(" (")
                    .append(match.get("uri")).append(")\n");
        }
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
        if (!isInitialized) {
            return;
        }
        if (kwargs != null && kwargs.get("session_id") != null) {
            sessionId = String.valueOf(kwargs.get("session_id"));
        }
        if (userMsg != null && !userMsg.isBlank()) {
            api.appendSessionMessage(endpoint, apiKey, account, user, agent, sessionId, "user", userMsg);
        }
        if (assistantMsg != null && !assistantMsg.isBlank()) {
            api.appendSessionMessage(endpoint, apiKey, account, user, agent, sessionId, "assistant", assistantMsg);
        }
    }

    /**
     * systemPromptBlock.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String systemPromptBlock() {
        return "# OpenViking Memory\n\n" + "Use `viking_search` to find knowledge (modes: auto/fast/deep).\n"
                + "Use `viking_read` to read content at a viking:// URI (levels: abstract/overview/full).\n"
                + "Use `viking_browse` to navigate the knowledge structure.\n"
                + "Use `viking_remember` to explicitly store facts.\n"
                + "Use `viking_add_resource` to index URLs/documents.";
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

    interface VikingApi {
        /**
         * search.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param payload payload
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        List<Map<String, Object>> search(String endpoint, String apiKey, String account, String user, String agent,
                Map<String, Object> payload) throws Exception;

        /**
         * read.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param uri uri
         * @param level level
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        Map<String, Object> read(String endpoint, String apiKey, String account, String user, String agent, String uri,
                String level) throws Exception;

        /**
         * browse.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param action action
         * @param browsePath browsePath
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        Map<String, Object> browse(String endpoint, String apiKey, String account, String user, String agent,
                String action, String browsePath) throws Exception;

        /**
         * appendSessionMessage.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param sessionId sessionId
         * @param role role
         * @param content content
         * @throws Exception Exception
         * @since 0.1.7
         */
        void appendSessionMessage(String endpoint, String apiKey, String account, String user, String agent,
                String sessionId, String role, String content) throws Exception;

        /**
         * addResource.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param payload payload
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        Map<String, Object> addResource(String endpoint, String apiKey, String account, String user, String agent,
                Map<String, Object> payload) throws Exception;
    }

    private static final class DefaultVikingApi implements VikingApi {
        private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

        /**
         * search.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param payload payload
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public List<Map<String, Object>> search(String endpoint, String apiKey, String account, String user,
                String agent, Map<String, Object> payload) throws Exception {
            Map<String, Object> response = post(endpoint, apiKey, account, user, agent, "/api/v1/search/find", payload);
            Object result = response.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                return List.of();
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String type : List.of("memories", "resources", "skills")) {
                Object value = resultMap.get(type);
                if (value instanceof List<?> items) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> raw) {
                            Map<String, Object> typed = castMap(raw);
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("uri", typed.getOrDefault("uri", ""));
                            entry.put("type", type.endsWith("s") ? type.substring(0, type.length() - 1) : type);
                            entry.put("score", typed.getOrDefault("score", 0.0));
                            entry.put("abstract", typed.getOrDefault("abstract", ""));
                            if (typed.get("relations") instanceof List<?> relations) {
                                List<String> related = new ArrayList<>();
                                for (Object relation : relations) {
                                    if (relation instanceof Map<?, ?> relationMap && relationMap.get("uri") != null) {
                                        related.add(String.valueOf(relationMap.get("uri")));
                                    }
                                    if (related.size() >= 3) {
                                        break;
                                    }
                                }
                                if (!related.isEmpty()) {
                                    entry.put("related", related);
                                }
                            }
                            if (typed.get("url") != null) {
                                entry.put("url", typed.get("url"));
                            }
                            entries.add(entry);
                        }
                    }
                }
            }
            entries.sort((left, right) -> Double.compare(
                    Double.parseDouble(String.valueOf(right.getOrDefault("score", 0.0))),
                    Double.parseDouble(String.valueOf(left.getOrDefault("score", 0.0)))));
            return entries;
        }

        /**
         * read.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param uri uri
         * @param level level
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> read(String endpoint, String apiKey, String account, String user, String agent,
                String uri, String level) throws Exception {
            String path = switch (level) {
                case "abstract" -> "/api/v1/content/abstract";
                case "full" -> "/api/v1/content/read";
                default -> "/api/v1/content/overview";
            };
            Map<String, Object> response = get(endpoint, apiKey, account, user, agent, path, Map.of("uri", uri));
            Object result = response.get("result");
            String content = result instanceof String
                    ? (String) result
                    : String.valueOf(castMap((Map<?, ?>) result).getOrDefault("content", ""));
            if (content.length() > 8000) {
                content = content.substring(0, 8000) + "\n\n[... truncated, use a more specific URI or abstract level]";
            }
            return Map.of("uri", uri, "level", level, "content", content);
        }

        /**
         * browse.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param action action
         * @param browsePath browsePath
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> browse(String endpoint, String apiKey, String account, String user, String agent,
                String action, String browsePath) throws Exception {
            String path = switch (action) {
                case "tree" -> "/api/v1/fs/tree";
                case "stat" -> "/api/v1/fs/stat";
                default -> "/api/v1/fs/ls";
            };
            Map<String, Object> response = get(endpoint, apiKey, account, user, agent, path, Map.of("uri", browsePath));
            Object result = response.get("result");
            if ("stat".equals(action)) {
                return Map.of("path", browsePath, "result", result);
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            if (result instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> raw) {
                        Map<String, Object> typed = castMap(raw);
                        entries.add(Map.of("name",
                                String.valueOf(typed.getOrDefault("rel_path", typed.getOrDefault("name", ""))), "uri",
                                String.valueOf(typed.getOrDefault("uri", "")), "type",
                                Boolean.TRUE.equals(typed.get("isDir")) ? "dir" : "file", "abstract",
                                String.valueOf(typed.getOrDefault("abstract", ""))));
                    }
                }
            }
            return Map.of("path", browsePath, "entries", entries);
        }

        /**
         * appendSessionMessage.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param sessionId sessionId
         * @param role role
         * @param content content
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public void appendSessionMessage(String endpoint, String apiKey, String account, String user, String agent,
                String sessionId, String role, String content) throws Exception {
            post(endpoint, apiKey, account, user, agent, "/api/v1/sessions/" + sessionId + "/messages",
                    Map.of("role", role, "content", content));
        }

        /**
         * addResource.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param payload payload
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> addResource(String endpoint, String apiKey, String account, String user,
                String agent, Map<String, Object> payload) throws Exception {
            return post(endpoint, apiKey, account, user, agent, "/api/v1/resources", payload);
        }

        /**
         * post.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param path path
         * @param body body
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        private Map<String, Object> post(String endpoint, String apiKey, String account, String user, String agent,
                String path, Map<String, Object> body) throws Exception {
            HttpRequest request = requestBuilder(endpoint, apiKey, account, user, agent, path)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return send(request);
        }

        /**
         * get.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param path path
         * @param params params
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        private Map<String, Object> get(String endpoint, String apiKey, String account, String user, String agent,
                String path, Map<String, Object> params) throws Exception {
            String url = normalizeBase(endpoint) + path;
            if (params != null && !params.isEmpty()) {
                StringBuilder builder = new StringBuilder(url);
                builder.append('?');
                boolean first = true;
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (!first) {
                        builder.append('&');
                    }
                    first = false;
                    builder.append(entry.getKey()).append('=').append(
                            java.net.URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                }
                url = builder.toString();
            }
            String pathWithQuery = url.replace(normalizeBase(endpoint), "");
            HttpRequest request = requestBuilder(endpoint, apiKey, account, user, agent, pathWithQuery).GET().build();
            return send(request);
        }

        /**
         * requestBuilder.
         * 
         * @param endpoint endpoint
         * @param apiKey apiKey
         * @param account account
         * @param user user
         * @param agent agent
         * @param path path
         * @return the result
         * @since 0.1.7
         */
        private HttpRequest.Builder requestBuilder(String endpoint, String apiKey, String account, String user,
                String agent, String path) {
            HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(normalizeBase(endpoint) + path)).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json").header("X-OpenViking-Account", account)
                        .header("X-OpenViking-User", user).header("X-OpenViking-Agent", agent);
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("X-API-Key", apiKey);
            }
            return builder;
        }

        /**
         * send.
         * 
         * @param request request
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        private Map<String, Object> send(HttpRequest request) throws Exception {
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenViking request failed with status " + response.statusCode() + ": " + response.body());
            }
            return MAPPER.readValue(response.body(), Map.class);
        }

        /**
         * normalizeBase.
         * 
         * @param endpoint endpoint
         * @return the result
         * @since 0.1.7
         */
        private static String normalizeBase(String endpoint) {
            return endpoint.replaceAll("/+$", "");
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
