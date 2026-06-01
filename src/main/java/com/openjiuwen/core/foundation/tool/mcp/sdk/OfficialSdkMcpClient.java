/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

/**
 * Adapter that hides the official Java SDK behind the existing {@link McpClient} contract.
 *
 * @since 0.1.7
 */
public class OfficialSdkMcpClient implements McpClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final OfficialMcpClientFactory.OfficialTransportConfig transportConfig;

    private McpSyncClient client;

    static final class ReadyStage {
        static final String CONNECT = "CONNECT";

        static final String INITIALIZE = "INITIALIZE";

        static final String LIST_TOOLS = "LIST_TOOLS";

        private ReadyStage() {
        }
    }

    private record ParsedHttpTransportTarget(String baseUri, String endpoint) {
    }

    private record TransportFailureContext(
            String stage,
            String clientType,
            String serverId,
            String serverPath,
            boolean isTimeout) {
    }

    static final class TransportException extends RuntimeException {
        private final String stage;
        private final String clientType;
        private final String serverId;
        private final String serverPath;
        private final boolean isTimeout;

        TransportException(
                String stage,
                String clientType,
                String serverId,
                String serverPath,
                boolean isTimeout,
                Throwable cause) {
            super(buildMessage(new TransportFailureContext(
                    stage,
                    clientType,
                    serverId,
                    serverPath,
                    isTimeout
            ), cause), cause);
            this.stage = stage;
            this.clientType = clientType;
            this.serverId = serverId;
            this.serverPath = serverPath;
            this.isTimeout = isTimeout;
        }

        String getStage() {
            return stage;
        }

        String getClientType() {
            return clientType;
        }

        String getServerId() {
            return serverId;
        }

        String getServerPath() {
            return serverPath;
        }

        boolean isTimeout() {
            return isTimeout;
        }

        private static String buildMessage(TransportFailureContext context,
                                           Throwable cause) {
            String detail = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                    ? "unknown error"
                    : cause.getMessage();
            String timeoutSuffix = context.isTimeout() ? " timeout=true" : "";
            return "Official MCP transport failed at stage=" + context.stage()
                    + ", clientType=" + context.clientType()
                    + ", serverId=" + context.serverId()
                    + ", serverPath=" + context.serverPath()
                    + timeoutSuffix
                    + ", reason=" + detail;
        }
    }

    /**
     * Creates an MCP client adapter for the provided server and transport config.
     *
     * @param config          MCP server config
     * @param transportConfig normalized transport config
     */
    public OfficialSdkMcpClient(McpServerConfig config,
                                OfficialMcpClientFactory.OfficialTransportConfig transportConfig) {
        this.config = config;
        this.transportConfig = transportConfig;
    }

    /**
     * Connects and initializes the remote MCP server.
     *
     * @param retryTimes retry count placeholder from the shared contract
     * @param timeout    timeout in seconds
     * @return {@code true} when the connection is ready
     */
    @Override
    public boolean connect(int retryTimes, float timeout) {
        if (client != null) {
            return true;
        }
        try {
            McpClientTransport transport = createTransport();
            var builder = io.modelcontextprotocol.client.McpClient.sync(transport);
            Optional<Duration> duration = toDuration(timeout);
            if (duration.isPresent()) {
                builder = builder.requestTimeout(duration.get()).initializationTimeout(duration.get());
            }
            client = builder.build();
        } catch (RuntimeException exception) {
            cleanupClientQuietly();
            throw transportFailure(ReadyStage.CONNECT, exception);
        }

        try {
            client.initialize();
        } catch (RuntimeException exception) {
            cleanupClientQuietly();
            throw transportFailure(ReadyStage.INITIALIZE, exception);
        }
        return true;
    }

    /**
     * Disconnects the underlying MCP client.
     *
     * @param timeout timeout in seconds
     * @return {@code true} after the client is closed or already absent
     */
    @Override
    public boolean disconnect(float timeout) {
        if (client == null) {
            return true;
        }
        client.closeGracefully();
        client = null;
        return true;
    }

    /**
     * Lists tool cards exposed by the MCP server.
     *
     * @param timeout timeout in seconds
     * @return available tool cards
     */
    @Override
    public List<Object> listTools(float timeout) {
        try {
            List<Object> results = new ArrayList<>();
            for (McpSchema.Tool tool : requireClient().listTools().tools()) {
                results.add(McpToolCard.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .serverName(config.getServerName())
                        .serverId(config.getServerId())
                        .inputParams(toInputParams(tool.inputSchema()))
                        .build());
            }
            return results;
        } catch (TransportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            cleanupClientQuietly();
            throw transportFailure(ReadyStage.LIST_TOOLS, exception);
        }
    }

    /**
     * Calls a tool on the MCP server and normalizes the SDK response.
     *
     * @param toolName  tool name
     * @param arguments tool arguments
     * @param timeout   timeout in seconds
     * @return normalized tool result
     */
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
        Map<String, Object> normalizedArguments = arguments == null ? Map.of() : arguments;
        McpSchema.CallToolResult result = requireClient().callTool(
                new McpSchema.CallToolRequest(toolName, normalizedArguments)
        );
        return OfficialMcpToolResultMapper.map(toolName, result);
    }

    /**
     * Looks up a single tool card by name.
     *
     * @param toolName tool name
     * @param timeout  timeout in seconds
     * @return matching tool card when present
     * @throws Exception when tool listing fails
     */
    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the configured MCP server path.
     *
     * @return server path
     */
    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    private McpSyncClient requireClient() {
        if (client == null) {
            throw new IllegalStateException("MCP client is not connected: " + config.getServerPath());
        }
        return client;
    }

    private McpClientTransport createTransport() {
        return switch (transportConfig.transportType()) {
            case STDIO -> createStdioTransport();
            case SSE -> createSseTransport();
            case STREAMABLE_HTTP -> createStreamableHttpTransport();
        };
    }

    private McpClientTransport createSseTransport() {
        ParsedHttpTransportTarget target = parseHttpTarget(transportConfig.serverPath());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        for (Map.Entry<String, String> entry : transportConfig.authHeaders().entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }
        return HttpClientSseClientTransport.builder(target.baseUri())
                .sseEndpoint(withAuthQuery(target.endpoint()))
                .requestBuilder(requestBuilder)
                .build();
    }

    private McpClientTransport createStreamableHttpTransport() {
        ParsedHttpTransportTarget target = parseHttpTarget(transportConfig.serverPath());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        for (Map.Entry<String, String> entry : transportConfig.authHeaders().entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }
        return HttpClientStreamableHttpTransport.builder(target.baseUri())
                .endpoint(withAuthQuery(target.endpoint()))
                .requestBuilder(requestBuilder)
                .build();
    }

    private String withAuthQuery(String endpoint) {
        if (transportConfig.authQueryParams() == null || transportConfig.authQueryParams().isEmpty()) {
            return endpoint;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : transportConfig.authQueryParams().entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return endpoint + (endpoint.contains("?") ? "&" : "?") + joiner;
    }

    private StdioClientTransport createStdioTransport() {
        ServerParameters serverParameters = ServerParameters.builder(transportConfig.command())
                .args(transportConfig.args())
                .env(transportConfig.env())
                .build();
        return new StdioClientTransport(serverParameters, new JacksonMcpJsonMapper(new ObjectMapper())) {
            @Override
            protected ProcessBuilder getProcessBuilder() {
                ProcessBuilder processBuilder = super.getProcessBuilder();
                if (transportConfig.cwd() != null && !transportConfig.cwd().isBlank()) {
                    processBuilder.directory(new File(transportConfig.cwd()));
                }
                return processBuilder;
            }
        };
    }

    private ParsedHttpTransportTarget parseHttpTarget(String serverPath) {
        URI serverUri = URI.create(serverPath);
        String path = serverUri.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return new ParsedHttpTransportTarget(
                    buildBaseUri(serverUri, "/"),
                    buildEndpointUri(".", serverUri.getRawQuery())
            );
        }

        int lastSlash = path.lastIndexOf('/');
        String basePath = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "/";
        String endpoint = path.substring(lastSlash + 1);
        if (endpoint.isBlank()) {
            endpoint = ".";
        }
        return new ParsedHttpTransportTarget(
                buildBaseUri(serverUri, basePath),
                buildEndpointUri(endpoint, serverUri.getRawQuery())
        );
    }

    private String buildBaseUri(URI serverUri, String path) {
        try {
            return new URI(serverUri.getScheme(), null, serverUri.getHost(), serverUri.getPort(), path, null, null)
                    .toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid MCP server URI: " + serverUri, exception);
        }
    }

    private String buildEndpointUri(String path, String query) {
        try {
            return new URI(null, null, path, (query == null || query.isBlank()) ? null : query, null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid MCP endpoint path: " + path, exception);
        }
    }

    private Optional<Duration> toDuration(float timeout) {
        BigDecimal timeoutSeconds = BigDecimal.valueOf(timeout);
        if (timeoutSeconds.compareTo(BigDecimal.valueOf(McpServerConfig.NO_TIMEOUT)) == 0
                || timeoutSeconds.signum() <= 0) {
            return Optional.empty();
        }
        long timeoutMillis = timeoutSeconds.movePointRight(3).longValue();
        return Optional.of(Duration.ofMillis(timeoutMillis));
    }

    private Map<String, Object> toInputParams(Object inputSchema) {
        if (inputSchema == null) {
            return Map.of();
        }
        Map<String, Object> converted = OBJECT_MAPPER.convertValue(inputSchema, new TypeReference<>() {
        });
        return converted == null ? Map.of() : new LinkedHashMap<>(converted);
    }

    private TransportException transportFailure(String stage, RuntimeException exception) {
        return new TransportException(
                stage,
                OfficialMcpClientFactory.normalizeClientType(config.getClientType()),
                config.getServerId(),
                config.getServerPath(),
                isTimeout(exception),
                exception
        );
    }

    private void cleanupClientQuietly() {
        if (client == null) {
            return;
        }
        try {
            client.closeGracefully();
        } catch (IllegalStateException ignored) {
            // Preserve the original transport failure.
        } finally {
            client = null;
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName();
            if (simpleName != null && simpleName.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
