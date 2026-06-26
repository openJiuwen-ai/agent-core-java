/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.auth.AuthCallback;
import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.auth.AuthType;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.mcp.McpBase;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SSE transport based MCP client.
 *
 * <p>Mirrors Python's {@code SseClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
 */
public class SseClient extends McpClient {

    public static final String CLIENT_NAME = "sse";
    public static final String __client_name__ = CLIENT_NAME;

    private static final double DEFAULT_SSE_TIMEOUT_SECONDS = 60.0D;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final String name;
    private final Map<String, String> authHeaders;
    private final Map<String, String> authQueryParams;
    private final String serverId;
    private final SseTransportFactory transportFactory;
    private final AuthTrigger authTrigger;

    private SseTransportSession session;
    private Object authProvider;
    private boolean disconnected;

    public SseClient(McpServerConfig config) {
        this(config, new SdkSseTransportFactory(), AuthTrigger.defaultAuth());
    }

    SseClient(McpServerConfig config, SseTransportFactory transportFactory, AuthTrigger authTrigger) {
        super(Objects.requireNonNull(config, "config"));
        this.config = config;
        this.name = config.getServerName();
        this.authHeaders = copyStringMap(config.getAuthHeaders());
        this.authQueryParams = copyStringMap(config.getAuthQueryParams());
        this.serverId = config.getServerId();
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
        this.authTrigger = Objects.requireNonNull(authTrigger, "authTrigger");
        this.disconnected = false;
    }

    public static String getClientName() {
        return CLIENT_NAME;
    }

    @Override
    public CompletableFuture<Boolean> connect(int retryTimes, double timeout) {
        return runAsync(() -> {
            try {
                Object authResult = authTrigger.trigger(ToolCallEvents.TOOL_AUTH, buildAuthConfig());
                authProvider = extractAuthProvider(authResult);
                double actualTimeout = timeout != McpServerConfig.NO_TIMEOUT ? timeout : DEFAULT_SSE_TIMEOUT_SECONDS;
                session = transportFactory.open(getServerPath(), actualTimeout, authProvider);
                session.initialize();
                disconnected = false;
                Loggers.TOOL.info("SSE client connected successfully to {}", getServerPath());
                return Boolean.TRUE;
            } catch (Exception error) {
                Loggers.TOOL.error("SSE connection failed to {}: {}", getServerPath(), error.getMessage());
                try {
                    disconnect(McpServerConfig.NO_TIMEOUT).join();
                } catch (CompletionException cleanupError) {
                    // Python cleanup errors are swallowed on the failed-connect path.
                }
                return Boolean.FALSE;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> disconnect(double timeout) {
        return runAsync(() -> {
            try {
                if (session != null) {
                    session.close();
                    session = null;
                }
                Loggers.TOOL.info("SSE client disconnected successfully");
                return Boolean.TRUE;
            } catch (Exception error) {
                Loggers.TOOL.error("SSE disconnection failed: {}", error.getMessage());
                return Boolean.FALSE;
            }
        });
    }

    @Override
    public CompletableFuture<List<Object>> listTools(double timeout) {
        return runAsync(() -> {
            SseTransportSession connectedSession = requireSession();
            try {
                List<Object> tools = new ArrayList<>();
                for (Object rawTool : connectedSession.listTools()) {
                    tools.add(toToolCard(rawTool));
                }
                Loggers.TOOL.info("Retrieved {} tools from SSE server", tools.size());
                return tools;
            } catch (Exception error) {
                Loggers.TOOL.error("Failed to list tools via SSE: {}", error.getMessage());
                throw error;
            }
        });
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, double timeout) {
        return runAsync(() -> {
            SseTransportSession connectedSession = requireSession();
            try {
                Loggers.TOOL.info("Calling tool '{}' via SSE with arguments: {}", toolName, arguments);
                Object toolResult = connectedSession.callTool(toolName, arguments);
                Object resultContent = McpBase.extractMcpToolResultContent(toolResult);
                Loggers.TOOL.info("Tool '{}' call completed via SSE", toolName);
                return resultContent;
            } catch (Exception error) {
                Loggers.TOOL.error("Tool call failed via SSE: {}", error.getMessage());
                throw error;
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Object>> getToolInfo(String toolName, double timeout) {
        return listTools(timeout).thenApply(tools -> {
            for (Object tool : tools) {
                if (toolName.equals(toolName(tool))) {
                    Loggers.TOOL.debug("Found tool info for '{}' via SSE", toolName);
                    return Optional.of(tool);
                }
            }
            Loggers.TOOL.warning("Tool '{}' not found via SSE", toolName);
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<Object>> listResources(double timeout) {
        return runAsync(() -> {
            SseTransportSession connectedSession = requireSession();
            try {
                return copyObjectList(connectedSession.listResources());
            } catch (Exception error) {
                Loggers.TOOL.error("Failed to list resources via SSE: {}", error.getMessage());
                throw error;
            }
        });
    }

    @Override
    public CompletableFuture<Object> readResource(String uri, double timeout) {
        return runAsync(() -> {
            SseTransportSession connectedSession = requireSession();
            try {
                return connectedSession.readResource(uri);
            } catch (Exception error) {
                Loggers.TOOL.error("Failed to read resource '{}' via SSE: {}", uri, error.getMessage());
                throw error;
            }
        });
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", CLIENT_NAME);
        metadata.put("client_type", CLIENT_TYPE);
        return metadata;
    }

    static Object extractAuthProvider(Object authResult) {
        if (authResult == null) {
            return null;
        }
        List<Object> authItems = authItems(authResult);
        for (int index = authItems.size() - 1; index >= 0; index--) {
            Object item = authItems.get(index);
            if (item == null) {
                continue;
            }
            if (item instanceof ToolAuthResult result && !result.isSuccess()) {
                continue;
            }
            Object authData = authData(item);
            if (authData instanceof Map<?, ?> authMap && authMap.containsKey("auth_provider")) {
                return authMap.get("auth_provider");
            }
        }
        return null;
    }

    Object getAuthProvider() {
        return authProvider;
    }

    boolean isDisconnected() {
        return disconnected;
    }

    private ToolAuthConfig buildAuthConfig() {
        Map<String, Object> authConfig = new LinkedHashMap<>();
        authConfig.put("auth_headers", authHeaders);
        authConfig.put("auth_query_params", authQueryParams);
        return new ToolAuthConfig(AuthType.HEADER_AND_QUERY.getValue(), authConfig, name, serverId);
    }

    private SseTransportSession requireSession() {
        if (session == null) {
            throw new RuntimeException("Not connected to SSE server");
        }
        return session;
    }

    private McpToolCard toToolCard(Object rawTool) {
        if (rawTool instanceof McpToolCard card) {
            return new McpToolCard(card.getId(), card.getName(), card.getDescription(), card.getInputParams(),
                    name, serverId);
        }
        if (rawTool instanceof McpSchema.Tool tool) {
            return new McpToolCard(null, tool.name(), nullToEmpty(tool.description()), toInputParams(tool.inputSchema()),
                    name, serverId);
        }
        if (rawTool instanceof Map<?, ?> map) {
            return new McpToolCard(null, stringValue(map.get("name")), stringValue(map.get("description")),
                    mapValue(map.get("inputSchema")), name, serverId);
        }
        return new McpToolCard(null, stringValue(toolAttribute(rawTool, "name")),
                stringValue(toolAttribute(rawTool, "description")),
                toInputParams(toolAttribute(rawTool, "inputSchema")), name, serverId);
    }

    private static Object toolName(Object tool) {
        if (tool instanceof McpToolCard card) {
            return card.getName();
        }
        if (tool instanceof Map<?, ?> map) {
            return map.get("name");
        }
        Object value = toolAttribute(tool, "name");
        return value == null ? tool : value;
    }

    private static List<Object> authItems(Object authResult) {
        if (authResult instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (authResult.getClass().isArray()) {
            int length = Array.getLength(authResult);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(Array.get(authResult, index));
            }
            return values;
        }
        return List.of(authResult);
    }

    private static Object authData(Object item) {
        if (item instanceof ToolAuthResult result) {
            return result.getAuthData();
        }
        Object value = invokeNoArgs(item, "getAuthData");
        if (value != null) {
            return value;
        }
        value = invokeNoArgs(item, "authData");
        if (value != null) {
            return value;
        }
        value = fieldValue(item, "auth_data");
        return value != null ? value : fieldValue(item, "authData");
    }

    private static Object toolAttribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        Object value = invokeNoArgs(target, name);
        if (value != null) {
            return value;
        }
        value = invokeNoArgs(target, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        return fieldValue(target, name);
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> toInputParams(Object inputSchema) {
        if (inputSchema == null) {
            return Map.of();
        }
        if (inputSchema instanceof Map<?, ?> map) {
            return mapValue(map);
        }
        Map<String, Object> converted = MAPPER.convertValue(inputSchema, new TypeReference<>() {
        });
        return converted == null ? Map.of() : new LinkedHashMap<>(converted);
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static List<Object> copyObjectList(List<?> values) {
        if (values == null) {
            return null;
        }
        return new ArrayList<>(values);
    }

    private static Map<String, String> copyStringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <T> CompletableFuture<T> runAsync(Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
    }

    /**
     * Authentication callback boundary.
     *
     * <p>Mirrors Python's {@code Runner.callback_framework.trigger(...)} call in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    @FunctionalInterface
    interface AuthTrigger {
        Object trigger(String event, ToolAuthConfig authConfig) throws Exception;

        static AuthTrigger noop() {
            return (event, authConfig) -> null;
        }

        static AuthTrigger defaultAuth() {
            return (event, authConfig) -> AuthCallback.unifiedAuthHandler(authConfig);
        }
    }

    /**
     * SSE transport creation boundary.
     *
     * <p>Mirrors Python's deferred {@code sse_client(...)} construction in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    interface SseTransportFactory {
        SseTransportSession open(String serverPath, double timeout, Object authProvider) throws Exception;
    }

    /**
     * MCP session operations used by the SSE client.
     *
     * <p>Mirrors Python's initialized {@code ClientSession} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    interface SseTransportSession extends AutoCloseable {
        void initialize() throws Exception;

        List<?> listTools() throws Exception;

        Object callTool(String toolName, Map<String, Object> arguments) throws Exception;

        List<?> listResources() throws Exception;

        Object readResource(String uri) throws Exception;

        @Override
        void close() throws Exception;
    }

    /**
     * Default SDK-backed SSE transport factory.
     *
     * <p>Mirrors Python's {@code sse_client(self._server_path, timeout=..., auth=...)} call in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    private static final class SdkSseTransportFactory implements SseTransportFactory {
        @Override
        public SseTransportSession open(String serverPath, double timeout, Object authProvider) {
            AuthHeaderAndQueryProvider headerAndQueryProvider = authProvider instanceof AuthHeaderAndQueryProvider provider
                    ? provider : null;
            McpClientTransport transport = createSseTransport(serverPath, headerAndQueryProvider);
            var builder = io.modelcontextprotocol.client.McpClient.sync(transport);
            if (timeout > 0 && timeout != McpServerConfig.NO_TIMEOUT) {
                Duration duration = Duration.ofMillis((long) (timeout * 1000));
                builder = builder.requestTimeout(duration).initializationTimeout(duration);
            }
            return new SdkSseTransportSession(builder.build());
        }

        private static McpClientTransport createSseTransport(String serverPath,
                                                             AuthHeaderAndQueryProvider authProvider) {
            String actualServerPath = authProvider == null
                    ? serverPath : authProvider.mergeQueryParams(URI.create(serverPath)).toASCIIString();
            ParsedHttpTarget target = parseHttpTarget(actualServerPath);
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(target.baseUri())
                    .sseEndpoint(target.endpoint());
            if (authProvider != null && !authProvider.getHeaders().isEmpty()) {
                builder = builder.customizeRequest(requestBuilder ->
                        authProvider.getHeaders().forEach(requestBuilder::header));
            }
            return builder.build();
        }

        private static ParsedHttpTarget parseHttpTarget(String serverPath) {
            URI serverUri = URI.create(serverPath);
            String path = serverUri.getRawPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return new ParsedHttpTarget(buildBaseUri(serverUri, "/"), buildEndpointUri(".", serverUri.getRawQuery()));
            }

            int lastSlash = path.lastIndexOf('/');
            String basePath = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "/";
            String endpoint = path.substring(lastSlash + 1);
            if (endpoint.isBlank()) {
                endpoint = ".";
            }
            return new ParsedHttpTarget(buildBaseUri(serverUri, basePath),
                    buildEndpointUri(endpoint, serverUri.getRawQuery()));
        }

        private static String buildBaseUri(URI serverUri, String path) {
            try {
                return new URI(serverUri.getScheme(), null, serverUri.getHost(), serverUri.getPort(), path, null, null)
                        .toASCIIString();
            } catch (URISyntaxException error) {
                throw new IllegalArgumentException("Invalid MCP server URI: " + serverUri, error);
            }
        }

        private static String buildEndpointUri(String path, String query) {
            try {
                return new URI(null, null, path, query == null || query.isBlank() ? null : query, null)
                        .toASCIIString();
            } catch (URISyntaxException error) {
                throw new IllegalArgumentException("Invalid MCP endpoint path: " + path, error);
            }
        }
    }

    /**
     * Parsed HTTP target for the Java SDK SSE transport.
     *
     * <p>Mirrors Python's raw {@code self._server_path} SSE URL in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    private record ParsedHttpTarget(String baseUri, String endpoint) {
    }

    /**
     * SDK-backed initialized MCP session.
     *
     * <p>Mirrors Python's {@code ClientSession} entered through {@code AsyncExitStack} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.</p>
     */
    private static final class SdkSseTransportSession implements SseTransportSession {
        private final McpSyncClient client;

        private SdkSseTransportSession(McpSyncClient client) {
            this.client = client;
        }

        @Override
        public void initialize() {
            client.initialize();
        }

        @Override
        public List<?> listTools() {
            return client.listTools().tools();
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments) {
            Map<String, Object> normalizedArguments = arguments == null ? Map.of() : arguments;
            return client.callTool(new McpSchema.CallToolRequest(toolName, normalizedArguments));
        }

        @Override
        public List<?> listResources() {
            return client.listResources().resources();
        }

        @Override
        public Object readResource(String uri) {
            return client.readResource(new McpSchema.ReadResourceRequest(uri)).contents();
        }

        @Override
        public void close() {
            client.closeGracefully();
        }
    }
}
