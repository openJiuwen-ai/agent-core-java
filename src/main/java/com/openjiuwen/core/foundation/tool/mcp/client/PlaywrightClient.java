/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpBase;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Playwright browser session based MCP client.
 *
 * <p>Mirrors Python's {@code PlaywrightClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.</p>
 */
public class PlaywrightClient implements McpClient {

    public static final String CLIENT_NAME = "playwright";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STDIO_SERVER_PARAMETERS_KEY = "stdio_server_parameters";
    private static final String RAW_SERVER_PATH_KEY = "server_path";

    private final McpServerConfig config;
    private final String name;
    private final TransportFactory transportFactory;

    private TransportSession session;
    private boolean disconnected;

    public PlaywrightClient(McpServerConfig config) {
        this(config, new SdkTransportFactory());
    }

    PlaywrightClient(McpServerConfig config, TransportFactory transportFactory) {
        this.config = config;
        this.name = config.getServerName();
        this.transportFactory = transportFactory;
        this.disconnected = false;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) {
        try {
            TransportSelection selection = selectTransport();
            this.session = transportFactory.open(selection.transportType(), selection.serverPath(), config, timeout);
            this.session.initialize();
            this.disconnected = false;
            Loggers.TOOL.info("Playwright client connected successfully");
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Playwright connection failed: {}", error.getMessage());
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
        if (disconnected) {
            Loggers.TOOL.info("Playwright client disconnected successfully");
            return true;
        }
        try {
            if (session != null) {
                session.close();
            }
            Loggers.TOOL.info("Playwright client disconnected successfully");
            disconnected = true;
            return true;
        } catch (RuntimeException error) {
            Loggers.TOOL.info("Playwright client disconnected successfully");
            disconnected = true;
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Playwright disconnection failed: {}", error.getMessage());
            return false;
        } finally {
            session = null;
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
            Loggers.TOOL.info("Retrieved {} browser tools from Playwright server", tools.size());
            return tools;
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to list browser tools: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        TransportSession connectedSession = requireSession();
        try {
            Loggers.TOOL.info("Calling browser tool '{}' with arguments: {}", toolName, arguments);
            Object toolResult = connectedSession.callTool(toolName, arguments);
            Object resultContent = McpBase.extractMcpToolResultContent(toolResult);
            Loggers.TOOL.info("Browser tool '{}' call completed", toolName);
            return resultContent;
        } catch (Exception error) {
            Loggers.TOOL.error("Browser tool call failed: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                Loggers.TOOL.debug("Found browser tool info for '{}'", toolName);
                return Optional.of(card);
            }
        }
        Loggers.TOOL.warning("Browser tool '{}' not found", toolName);
        return Optional.empty();
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    private TransportSession requireSession() {
        if (session == null) {
            throw new RuntimeException("Not connected to Playwright server");
        }
        return session;
    }

    private TransportSelection selectTransport() {
        Object serverPath = rawServerPath();
        if (serverPath instanceof ServerParameters) {
            Loggers.TOOL.debug("Using Stdio transport for Playwright client");
            return new TransportSelection(TransportType.STDIO, serverPath);
        }
        if (serverPath instanceof String value && (value.startsWith("http://") || value.startsWith("https://"))) {
            Loggers.TOOL.debug("Using SSE transport for Playwright client");
            return new TransportSelection(TransportType.SSE, value);
        }
        String typeName = serverPath == null ? "null" : serverPath.getClass().getName();
        throw new IllegalArgumentException("Unsupported server_path type: " + typeName);
    }

    private Object rawServerPath() {
        Map<String, Object> params = config.getParams();
        if (params != null && params.containsKey(STDIO_SERVER_PARAMETERS_KEY)) {
            return params.get(STDIO_SERVER_PARAMETERS_KEY);
        }
        if (params != null && params.containsKey(RAW_SERVER_PATH_KEY)) {
            return params.get(RAW_SERVER_PATH_KEY);
        }
        return config.getServerPath();
    }

    private McpToolCard toToolCard(Object rawTool) {
        if (rawTool instanceof McpToolCard card) {
            return new McpToolCard(card.getId(), card.getName(), card.getDescription(), card.getInputParams(),
                    name, config.getServerId());
        }
        if (rawTool instanceof McpSchema.Tool tool) {
            return new McpToolCard(null, tool.name(), nullToEmpty(tool.description()), toInputParams(tool.inputSchema()),
                    name, config.getServerId());
        }
        if (rawTool instanceof Map<?, ?> map) {
            return new McpToolCard(null, stringValue(map.get("name")), stringValue(map.get("description")),
                    mapValue(map.get("inputSchema")), name, config.getServerId());
        }
        return new McpToolCard(null, stringValue(rawTool), "", Map.of(), name, config.getServerId());
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

    /**
     * Mirrors Python's runtime transport branch in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    enum TransportType {
        STDIO,
        SSE
    }

    /**
     * Mirrors Python's selected {@code server_path} branch in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    record TransportSelection(TransportType transportType, Object serverPath) {
    }

    /**
     * Mirrors Python's deferred import and context entry boundary in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    interface TransportFactory {
        TransportSession open(TransportType transportType, Object serverPath, McpServerConfig config, float timeout)
                throws Exception;
    }

    /**
     * Mirrors Python's MCP client/session context managed by {@code AsyncExitStack} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    interface TransportSession extends AutoCloseable {
        void initialize();

        List<?> listTools();

        Object callTool(String toolName, Map<String, Object> arguments);

        @Override
        void close() throws Exception;
    }

    /**
     * Mirrors Python's default stdio/SSE client creation in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    private static final class SdkTransportFactory implements TransportFactory {
        @Override
        public TransportSession open(TransportType transportType, Object serverPath, McpServerConfig config,
                                     float timeout) {
            McpClientTransport transport = switch (transportType) {
                case STDIO -> new StdioClientTransport((ServerParameters) serverPath,
                        new JacksonMcpJsonMapper(new ObjectMapper()));
                case SSE -> createSseTransport(String.valueOf(serverPath));
            };
            var builder = io.modelcontextprotocol.client.McpClient.sync(transport);
            if (timeout > 0 && timeout != McpServerConfig.NO_TIMEOUT) {
                Duration duration = Duration.ofMillis((long) (timeout * 1000));
                builder = builder.requestTimeout(duration).initializationTimeout(duration);
            }
            return new SdkTransportSession(builder.build());
        }

        private static McpClientTransport createSseTransport(String serverPath) {
            ParsedHttpTarget target = parseHttpTarget(serverPath);
            return HttpClientSseClientTransport.builder(target.baseUri())
                    .sseEndpoint(target.endpoint())
                    .build();
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
     * Mirrors Python's SSE URL passed to {@code sse_client} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
     */
    private record ParsedHttpTarget(String baseUri, String endpoint) {
    }

    /**
     * Mirrors Python's initialized {@code ClientSession} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
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
        public void close() {
            client.closeGracefully();
        }
    }
}
