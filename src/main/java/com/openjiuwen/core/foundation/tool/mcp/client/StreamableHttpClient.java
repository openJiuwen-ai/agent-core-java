/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.auth.AuthStrategyRegistry;
import com.openjiuwen.core.foundation.tool.auth.AuthType;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.mcp.McpBase;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Streamable HTTP transport based MCP client.
 *
 * <p>Mirrors Python's {@code StreamableHttpClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.</p>
 */
public class StreamableHttpClient implements McpClient {

    public static final List<String> CLIENT_NAMES = List.of("streamable-http", "streamable_http");

    private static final String DEFAULT_CLIENT_NAME = "streamable-http";
    private static final float DEFAULT_TIMEOUT_SECONDS = 60.0F;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final String name;
    private final String serverId;
    private final Map<String, String> authHeaders;
    private final Map<String, String> authQueryParams;
    private final TransportFactory transportFactory;

    private TransportSession session;
    private AuthHeaderAndQueryProvider authProvider;
    private boolean disconnected;

    public StreamableHttpClient(McpServerConfig config) {
        this(config, new SdkTransportFactory());
    }

    StreamableHttpClient(McpServerConfig config, TransportFactory transportFactory) {
        McpServerConfig resolvedConfig = normalizeConfig(config);
        this.config = resolvedConfig;
        this.name = resolvedConfig.getServerName();
        this.serverId = resolvedConfig.getServerId();
        this.authHeaders = copyStringMap(resolvedConfig.getAuthHeaders());
        this.authQueryParams = copyStringMap(resolvedConfig.getAuthQueryParams());
        this.transportFactory = transportFactory;
        this.disconnected = false;
    }

    public StreamableHttpClient(String serverPath) {
        this(serverPath, null, null, null);
    }

    public StreamableHttpClient(String serverPath, String name, Map<String, String> authHeaders,
                                Map<String, String> authQueryParams) {
        this(normalizeConfig(serverPath, name, authHeaders, authQueryParams), new SdkTransportFactory());
    }

    StreamableHttpClient(String serverPath, String name, Map<String, String> authHeaders,
                         Map<String, String> authQueryParams, TransportFactory transportFactory) {
        this(normalizeConfig(serverPath, name, authHeaders, authQueryParams), transportFactory);
    }

    @Override
    public boolean connect(int retryTimes, float timeout) {
        try {
            authProvider = resolveAuthProvider();
            float actualTimeout = timeout != McpServerConfig.NO_TIMEOUT ? timeout : DEFAULT_TIMEOUT_SECONDS;
            session = transportFactory.open(config.getServerPath(), config, actualTimeout, authProvider);
            session.initialize();
            disconnected = false;
            Loggers.TOOL.info("Streamable-http client connected successfully to {}", config.getServerPath());
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Streamable-http connection failed to {}: {}", config.getServerPath(),
                    error.getMessage());
            try {
                disconnect(timeout);
            } catch (Exception ignored) {
                // Python swallows cleanup errors on failed connect and returns False.
            }
            return false;
        }
    }

    @Override
    public boolean disconnect(float timeout) {
        try {
            if (session != null) {
                session.close();
            }
            session = null;
            authProvider = null;
            disconnected = true;
            Loggers.TOOL.info("Streamable-http client disconnected successfully");
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Streamable-http disconnection failed: {}", error.getMessage());
            return false;
        }
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        TransportSession connectedSession = requireSession();
        try {
            List<Object> tools = new ArrayList<>();
            for (Object rawTool : connectedSession.listTools()) {
                tools.add(toToolCard(rawTool));
            }
            Loggers.TOOL.info("Retrieved {} tools from streamable-http server", tools.size());
            return tools;
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to list tools via streamable-http: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        TransportSession connectedSession = requireSession();
        try {
            Map<String, Object> normalizedArguments = arguments == null ? Map.of() : arguments;
            Loggers.TOOL.info("Calling tool '{}' via streamable-http with arguments: {}", toolName,
                    normalizedArguments);
            Object toolResult = connectedSession.callTool(toolName, normalizedArguments);
            Object resultContent = McpBase.extractMcpToolResultContent(toolResult);
            Loggers.TOOL.info("Tool '{}' call completed via streamable-http", toolName);
            return resultContent;
        } catch (Exception error) {
            Loggers.TOOL.error("Tool call failed via streamable-http: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                Loggers.TOOL.debug("Found tool info for '{}' via streamable-http", toolName);
                return Optional.of(card);
            }
        }
        Loggers.TOOL.warning("Tool '{}' not found via streamable-http", toolName);
        return Optional.empty();
    }

    @Override
    public List<Object> listResources(float timeout) throws Exception {
        TransportSession connectedSession = requireSession();
        try {
            return new ArrayList<>(connectedSession.listResources());
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to list resources via streamable-http: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Object readResource(String uri, float timeout) throws Exception {
        TransportSession connectedSession = requireSession();
        try {
            return connectedSession.readResource(uri);
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to read resource '{}' via streamable-http: {}", uri, error.getMessage());
            throw error;
        }
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    public String getName() {
        return name;
    }

    boolean isDisconnected() {
        return disconnected;
    }

    private TransportSession requireSession() {
        if (session == null) {
            throw new RuntimeException("Not connected to streamable-http server");
        }
        return session;
    }

    private AuthHeaderAndQueryProvider resolveAuthProvider() {
        Map<String, Object> authConfig = new LinkedHashMap<>();
        authConfig.put("auth_headers", authHeaders);
        authConfig.put("auth_query_params", authQueryParams);
        ToolAuthResult authResult = AuthStrategyRegistry.executeAuth(new ToolAuthConfig(
                AuthType.HEADER_AND_QUERY.getValue(),
                authConfig,
                name,
                serverId
        ));
        if (authResult != null && authResult.isSuccess()) {
            Object provider = authResult.getAuthData().get("auth_provider");
            if (provider instanceof AuthHeaderAndQueryProvider typedProvider) {
                return typedProvider;
            }
        }
        return null;
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
            Object inputSchema = map.containsKey("inputSchema") ? map.get("inputSchema") : map.get("input_schema");
            return new McpToolCard(null, stringValue(map.get("name")), stringValue(map.get("description")),
                    mapValue(inputSchema), name, serverId);
        }
        return new McpToolCard(null, stringValue(rawTool), "", Map.of(), name, serverId);
    }

    private static McpServerConfig normalizeConfig(McpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must be McpServerConfig or String URL");
        }
        return config;
    }

    private static McpServerConfig normalizeConfig(String serverPath, String name, Map<String, String> authHeaders,
                                                   Map<String, String> authQueryParams) {
        String resolvedName = (name == null ? "" : name).trim();
        if (resolvedName.isEmpty()) {
            resolvedName = DEFAULT_CLIENT_NAME;
        }
        return new McpServerConfig(
                resolvedName,
                resolvedName,
                serverPath,
                DEFAULT_CLIENT_NAME,
                Map.of(),
                authHeaders == null ? Map.of() : authHeaders,
                authQueryParams == null ? Map.of() : authQueryParams
        );
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

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        return source == null || source.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(source));
    }

    /**
     * Mirrors Python's deferred {@code streamable_http_client(...)} context boundary in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    interface TransportFactory {
        TransportSession open(String serverPath, McpServerConfig config, float timeout,
                              AuthHeaderAndQueryProvider authProvider) throws Exception;
    }

    /**
     * Mirrors Python's initialized {@code ClientSession} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    interface TransportSession extends AutoCloseable {
        void initialize();

        List<?> listTools();

        Object callTool(String toolName, Map<String, Object> arguments);

        List<?> listResources();

        Object readResource(String uri);

        @Override
        void close() throws Exception;
    }

    /**
     * Mirrors Python's default MCP streamable HTTP client construction in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    private static final class SdkTransportFactory implements TransportFactory {
        @Override
        public TransportSession open(String serverPath, McpServerConfig config, float timeout,
                                     AuthHeaderAndQueryProvider authProvider) {
            McpClientTransport transport = createStreamableHttpTransport(serverPath, timeout, authProvider);
            var builder = io.modelcontextprotocol.client.McpClient.sync(transport);
            if (timeout > 0 && timeout != McpServerConfig.NO_TIMEOUT) {
                Duration duration = Duration.ofMillis((long) (timeout * 1000));
                builder = builder.requestTimeout(duration).initializationTimeout(duration);
            }
            return new SdkTransportSession(builder.build());
        }

        private static McpClientTransport createStreamableHttpTransport(String serverPath, float timeout,
                                                                        AuthHeaderAndQueryProvider authProvider) {
            ParsedHttpTarget target = parseHttpTarget(serverPath);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            if (authProvider != null) {
                authProvider.getHeaders().forEach(requestBuilder::header);
            }
            var builder = HttpClientStreamableHttpTransport.builder(target.baseUri())
                    .endpoint(withAuthQuery(target.endpoint(), authProvider))
                    .requestBuilder(requestBuilder);
            if (timeout > 0 && timeout != McpServerConfig.NO_TIMEOUT) {
                builder = builder.connectTimeout(Duration.ofMillis((long) (timeout * 1000)));
            }
            return builder.build();
        }

        private static ParsedHttpTarget parseHttpTarget(String serverPath) {
            URI serverUri = URI.create(serverPath);
            String path = serverUri.getRawPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return new ParsedHttpTarget(buildBaseUri(serverUri, "/"),
                        buildEndpointUri(".", serverUri.getRawQuery()));
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

        private static String withAuthQuery(String endpoint, AuthHeaderAndQueryProvider authProvider) {
            if (authProvider == null || authProvider.getQueryParams().isEmpty()) {
                return endpoint;
            }
            StringJoiner joiner = new StringJoiner("&");
            for (Map.Entry<String, String> entry : authProvider.getQueryParams().entrySet()) {
                joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            return endpoint + (endpoint.contains("?") ? "&" : "?") + joiner;
        }
    }

    /**
     * Mirrors Python's streamable HTTP endpoint split passed to
     * {@code streamable_http_client} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    private record ParsedHttpTarget(String baseUri, String endpoint) {
    }

    /**
     * Mirrors Python's initialized {@code ClientSession} wrapper in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    private static final class SdkTransportSession implements TransportSession {
        private final McpSyncClient client;

        private SdkTransportSession(McpSyncClient client) {
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
