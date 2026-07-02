/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;
import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Full bidirectional memory via OpenViking context database.
 * <p>
 * Tools: viking_search, viking_read, viking_browse, viking_remember, viking_add_resource.
 * Session-based: sync_turn records turns, on_session_end commits session.
 * <p>
 * Mirrors Python's {@code OpenVikingMemoryProvider} in
 * {@code openjiuwen/core/memory/external/openviking_memory_provider.py}.
 */
public class OpenVikingMemoryProvider extends MemoryProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Executor IO_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor("openviking-memory-provider-io");

    // Tool schemas
    private static final Map<String, Object> VIKING_SEARCH_SCHEMA = Map.of(
            "name", "viking_search",
            "description", "在知识库中进行全域搜索.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "搜索查询词."),
                            "mode", Map.of("type", "string", "enum", List.of("auto", "fast", "deep"),
                                    "description", "搜索模式（默认：auto）."),
                            "top_k", Map.of("type", "integer", "description", "最大返回结果数（默认10）.")
                    ),
                    "required", List.of("query")
            )
    );

    private static final Map<String, Object> VIKING_READ_SCHEMA = Map.of(
            "name", "viking_read",
            "description", "读取 viking:// URI 上的内容.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "uri", Map.of("type", "string", "description", "要读取的 viking:// URI."),
                            "detail", Map.of("type", "string", "enum", List.of("abstract", "overview", "full"),
                                    "description", "详情级别（默认：overview）.")
                    ),
                    "required", List.of("uri")
            )
    );

    private static final Map<String, Object> VIKING_BROWSE_SCHEMA = Map.of(
            "name", "viking_browse",
            "description", "浏览知识库结构.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "action", Map.of("type", "string", "enum", List.of("list", "tree", "stat"),
                                    "description", "浏览操作."),
                            "path", Map.of("type", "string", "description", "浏览路径（默认：/）.")
                    ),
                    "required", List.of("action")
            )
    );

    private static final Map<String, Object> VIKING_REMEMBER_SCHEMA = Map.of(
            "name", "viking_remember",
            "description", "显式存储一个事实或偏好.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "content", Map.of("type", "string", "description", "要记住的事实."),
                            "category", Map.of("type", "string", "enum",
                                    List.of("preference", "entity", "event", "case", "pattern"),
                                    "description", "记忆类别.")
                    ),
                    "required", List.of("content")
            )
    );

    private static final Map<String, Object> VIKING_ADD_RESOURCE_SCHEMA = Map.of(
            "name", "viking_add_resource",
            "description", "索引一个 URL 或文档以供后续搜索.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "url", Map.of("type", "string", "description", "要索引的 URL 或文件路径."),
                            "title", Map.of("type", "string", "description", "可选标题.")
                    ),
                    "required", List.of("url")
            )
    );

    private final String endpoint;
    private final String apiKey;
    private final String account;
    private final String user;
    private final String agent;
    private final HttpClientFactory httpClientFactory;
    private HttpClient httpClient;
    private String sessionId;

    @FunctionalInterface
    interface HttpClientFactory {
        HttpClient create();
    }

    public OpenVikingMemoryProvider() {
        this("", "", "", "", "");
    }

    public OpenVikingMemoryProvider(String endpoint, String apiKey, String account, String user, String agent) {
        this(endpoint, apiKey, account, user, agent, System.getenv(), () -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    OpenVikingMemoryProvider(String endpoint, String apiKey, String account, String user, String agent,
                             Map<String, String> env, HttpClientFactory httpClientFactory) {
        Map<String, String> envVars = env == null ? Map.of() : env;
        this.endpoint = Optional.ofNullable(endpoint).filter(s -> !s.isEmpty())
                .orElseGet(() -> envVars.getOrDefault("OPENVIKING_ENDPOINT", ""));
        this.apiKey = Optional.ofNullable(apiKey).filter(s -> !s.isEmpty())
                .orElseGet(() -> envVars.getOrDefault("OPENVIKING_API_KEY", ""));
        this.account = Optional.ofNullable(account).filter(s -> !s.isEmpty())
                .orElseGet(() -> envVars.getOrDefault("OPENVIKING_ACCOUNT", "default"));
        this.user = Optional.ofNullable(user).filter(s -> !s.isEmpty())
                .orElseGet(() -> envVars.getOrDefault("OPENVIKING_USER", "default"));
        this.agent = Optional.ofNullable(agent).filter(s -> !s.isEmpty())
                .orElseGet(() -> envVars.getOrDefault("OPENVIKING_AGENT", "hermes"));
        this.httpClientFactory = Objects.requireNonNull(httpClientFactory, "httpClientFactory");
    }

    @Override
    public String getName() {
        return "openviking";
    }

    @Override
    public boolean isAvailable() {
        return endpoint != null && !endpoint.isEmpty();
    }

    @Override
    public boolean isInitialized() {
        return httpClient != null;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            sessionId = kwargs != null && kwargs.containsKey("session_id")
                    ? String.valueOf(kwargs.get("session_id")) : "";
            try {
                httpClient = httpClientFactory.create();
                // Health check
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint.replaceAll("/+$", "") + "/health"))
                        .timeout(Duration.ofSeconds(10))
                        .GET();
                addAuthHeaders(reqBuilder);
                HttpRequest req = reqBuilder.build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    Loggers.MEMORY.warn("OpenViking at {} not reachable", endpoint);
                    httpClient = null;
                }
            } catch (Exception e) {
                Loggers.MEMORY.warn("OpenViking init failed: {}", e.getMessage());
                httpClient = null;
            }
        }, IO_EXECUTOR);
    }

    @Override
    public String systemPromptBlock() {
        return "# OpenViking Memory\n\n"
                + "Use `viking_search` to find knowledge (modes: auto/fast/deep).\n"
                + "Use `viking_read` to read content at a viking:// URI (levels: abstract/overview/full).\n"
                + "Use `viking_browse` to navigate the knowledge structure.\n"
                + "Use `viking_remember` to explicitly store facts.\n"
                + "Use `viking_add_resource` to index URLs/documents.";
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        if (httpClient == null || query == null || query.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> payload = Map.of("query", query, "top_k", 5);
                Map<String, Object> resp = post("/api/v1/search/find", payload);
                Map<String, Object> result = (Map<String, Object>) resp.getOrDefault("result", Map.of());
                List<String> parts = new ArrayList<>();
                for (String ctxType : List.of("memories", "resources")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOrDefault(ctxType, List.of());
                    for (int i = 0; i < Math.min(3, items.size()); i++) {
                        Map<String, Object> item = items.get(i);
                        String uri = String.valueOf(item.getOrDefault("uri", ""));
                        String abstract_ = String.valueOf(item.getOrDefault("abstract", ""));
                        double score = item.get("score") instanceof Number n ? n.doubleValue() : 0.0;
                        if (!abstract_.isEmpty()) {
                            parts.add(String.format("- [%.2f] %s (%s)", score, abstract_, uri));
                        }
                    }
                }
                return parts.isEmpty() ? "" : "## OpenViking Context\n" + String.join("\n", parts);
            } catch (Exception e) {
                Loggers.MEMORY.debug("OpenViking prefetch failed: {}", e.getMessage());
                return "";
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        if (httpClient == null) {
            return CompletableFuture.completedFuture(null);
        }
        String sid = kwargs != null && kwargs.containsKey("session_id")
                ? String.valueOf(kwargs.get("session_id")) : sessionId;
        return CompletableFuture.runAsync(() -> {
            try {
                post("/api/v1/sessions/" + sid + "/messages",
                        Map.of("role", "user", "content", truncate(userMsg, 4000)));
                post("/api/v1/sessions/" + sid + "/messages",
                        Map.of("role", "assistant", "content", truncate(assistantMsg, 4000)));
            } catch (Exception e) {
                Loggers.MEMORY.debug("OpenViking sync failed: {}", e.getMessage());
            }
        }, IO_EXECUTOR);
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(VIKING_SEARCH_SCHEMA, VIKING_READ_SCHEMA, VIKING_BROWSE_SCHEMA,
                VIKING_REMEMBER_SCHEMA, VIKING_ADD_RESOURCE_SCHEMA);
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        if (httpClient == null) {
            return CompletableFuture.completedFuture("{\"error\": \"OpenViking not connected\"}");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Object result;
                switch (toolName) {
                    case "viking_search" -> result = handleSearch(args);
                    case "viking_read" -> result = handleRead(args);
                    case "viking_browse" -> result = handleBrowse(args);
                    case "viking_remember" -> result = handleRemember(args);
                    case "viking_add_resource" -> result = handleAddResource(args);
                    default -> {
                        return "{\"error\": \"Unknown tool: " + toolName + "\"}";
                    }
                }
                return MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> onSessionEnd(List<Map<String, Object>> messages) {
        if (httpClient == null || sessionId == null || sessionId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                post("/api/v1/sessions/" + sessionId + "/commit", Map.of());
            } catch (Exception e) {
                Loggers.MEMORY.debug("OpenViking session commit failed: {}", e.getMessage());
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        HttpClient client = httpClient;
        if (client != null) {
            try {
                // HttpClient.close() is available on JDK 21+; keep the Java 17 build compatible.
                if (client instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Exception e) {
                Loggers.MEMORY.debug("OpenViking client close failed: {}", e.getMessage());
            } finally {
                httpClient = null;
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Tool handlers ====================

    private Map<String, Object> handleSearch(Map<String, Object> args) throws Exception {
        String query = String.valueOf(args.getOrDefault("query", ""));
        if (query.isEmpty()) return Map.of("error", "query is required");
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("query", query));
        String mode = String.valueOf(args.getOrDefault("mode", "auto"));
        if (!"auto".equals(mode)) payload.put("mode", mode);
        if (args.get("scope") != null) payload.put("target_uri", args.get("scope"));
        if (args.get("limit") != null) payload.put("top_k", args.get("limit"));

        Map<String, Object> resp = post("/api/v1/search/find", payload);
        Map<String, Object> resultData = (Map<String, Object>) resp.getOrDefault("result", Map.of());
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (String ctxType : List.of("memories", "resources", "skills")) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) resultData.getOrDefault(ctxType, List.of());
            for (Map<String, Object> item : items) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uri", item.getOrDefault("uri", ""));
                entry.put("type", ctxType.replaceAll("s$", ""));
                Object rawScore = item.get("score");
                entry.put("score", rawScore instanceof Number n ? Math.round(n.doubleValue() * 1000.0) / 1000.0 : 0.0);
                entry.put("abstract", item.getOrDefault("abstract", ""));
                formatted.add(entry);
            }
        }
        formatted.sort((a, b) -> {
            double sa = a.get("score") instanceof Number n ? n.doubleValue() : 0;
            double sb = b.get("score") instanceof Number n ? n.doubleValue() : 0;
            return Double.compare(sb, sa);
        });
        return Map.of("results", formatted, "total", resultData.getOrDefault("total", formatted.size()));
    }

    private Map<String, Object> handleRead(Map<String, Object> args) throws Exception {
        String uri = String.valueOf(args.getOrDefault("uri", ""));
        if (uri.isEmpty()) return Map.of("error", "uri is required");
        String level = args.containsKey("detail") ? String.valueOf(args.get("detail")) :
                args.containsKey("level") ? String.valueOf(args.get("level")) : "overview";
        Map<String, Object> resp = switch (level) {
            case "abstract" -> get("/api/v1/content/abstract", Map.of("uri", uri));
            case "full" -> get("/api/v1/content/read", Map.of("uri", uri));
            default -> get("/api/v1/content/overview", Map.of("uri", uri));
        };
        Object content = resp.getOrDefault("result", "");
        if (content instanceof Map<?, ?> m) content = m.get("content") != null ? m.get("content") : "";
        String contentStr = String.valueOf(content);
        if (contentStr.length() > 8000) {
            contentStr = contentStr.substring(0, 8000) + "\n\n[... truncated, use a more specific URI or abstract level]";
        }
        return Map.of("uri", uri, "level", level, "content", contentStr);
    }

    private Map<String, Object> handleBrowse(Map<String, Object> args) throws Exception {
        String action = String.valueOf(args.getOrDefault("action", "list"));
        String browsePath = String.valueOf(args.getOrDefault("path", "viking://"));
        Map<String, String> endpointMap = Map.of("tree", "/api/v1/fs/tree", "list", "/api/v1/fs/ls", "stat", "/api/v1/fs/stat");
        String path = endpointMap.getOrDefault(action, "/api/v1/fs/ls");
        Map<String, Object> resp = get(path, Map.of("uri", browsePath));
        Object entries = resp.getOrDefault("result", Map.of());
        if (("list".equals(action) || "tree".equals(action)) && entries instanceof List<?> list) {
            List<Map<String, Object>> formatted = new ArrayList<>();
            for (int i = 0; i < Math.min(50, list.size()); i++) {
                Map<String, Object> e = (Map<String, Object>) list.get(i);
                formatted.add(Map.of(
                        "name", e.getOrDefault("rel_path", e.getOrDefault("name", "")),
                        "uri", e.getOrDefault("uri", ""),
                        "type", Boolean.TRUE.equals(e.get("isDir")) ? "dir" : "file",
                        "abstract", e.getOrDefault("abstract", "")
                ));
            }
            return Map.of("path", browsePath, "entries", formatted);
        }
        return Map.of("path", browsePath, "result", entries);
    }

    private Map<String, Object> handleRemember(Map<String, Object> args) throws Exception {
        String content = String.valueOf(args.getOrDefault("content", ""));
        if (content.isEmpty()) return Map.of("error", "content is required");
        String category = String.valueOf(args.getOrDefault("category", ""));
        String text = category.isEmpty() ? "[Remember] " + content : "[Remember — " + category + "] " + content;
        post("/api/v1/sessions/" + sessionId + "/messages",
                Map.of("role", "user", "parts", List.of(Map.of("type", "text", "text", text))));
        return Map.of("status", "stored",
                "message", "Memory recorded. Will be extracted and indexed on session commit.");
    }

    private Map<String, Object> handleAddResource(Map<String, Object> args) throws Exception {
        String url = String.valueOf(args.getOrDefault("url", ""));
        if (url.isEmpty()) return Map.of("error", "url is required");
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("path", url));
        if (args.get("reason") != null) payload.put("reason", args.get("reason"));
        Map<String, Object> resp = post("/api/v1/resources", payload);
        Map<String, Object> resData = (Map<String, Object>) resp.getOrDefault("result", Map.of());
        return Map.of("status", "added",
                "root_uri", resData.getOrDefault("root_uri", ""),
                "message", "Resource queued for processing. Use viking_search after a moment to find it.");
    }

    // ==================== HTTP helpers ====================

    private Map<String, Object> post(String path, Map<String, Object> body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        addAuthHeaders(reqBuilder);
        HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return (Map<String, Object>) MAPPER.readValue(resp.body(), Map.class);
    }

    private Map<String, Object> get(String path, Map<String, String> params) throws Exception {
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b).orElse("");
        String url = endpoint.replaceAll("/+$", "") + path + (query.isEmpty() ? "" : "?" + query);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();
        addAuthHeaders(reqBuilder);
        HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return (Map<String, Object>) MAPPER.readValue(resp.body(), Map.class);
    }

    private void addAuthHeaders(HttpRequest.Builder builder) {
        builder.header("X-OpenViking-Account", account)
               .header("X-OpenViking-User", user)
               .header("X-OpenViking-Agent", agent);
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("X-API-Key", apiKey);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
