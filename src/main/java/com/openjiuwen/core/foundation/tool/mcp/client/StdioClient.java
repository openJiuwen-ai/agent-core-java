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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stdio transport based MCP client.
 *
 * <p>Mirrors Python's {@code StdioClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
 */
public class StdioClient implements McpClient {

    public static final String CLIENT_NAME = "stdio";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_ENCODING_HANDLERS = Set.of("strict", "ignore", "replace");

    private final McpServerConfig config;
    private final String name;
    private final Map<String, Object> params;
    private final SessionFactory sessionFactory;

    private StdioSession session;
    private boolean disconnected;

    public StdioClient(McpServerConfig config) {
        this(config, JsonRpcProcessSession::open);
    }

    StdioClient(McpServerConfig config, SessionFactory sessionFactory) {
        this.config = Objects.requireNonNull(config, "config");
        this.name = config.getServerName();
        this.params = config.getParams() != null ? new LinkedHashMap<>(config.getParams()) : new LinkedHashMap<>();
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.disconnected = false;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) {
        try {
            StdioServerParameters serverParameters = buildServerParameters();
            session = sessionFactory.open(serverParameters);
            session.initialize(timeout);
            disconnected = false;
            Loggers.TOOL.info("Stdio client connected successfully");
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Stdio connection failed: {}", error.getMessage());
            try {
                disconnect(McpServerConfig.NO_TIMEOUT);
            } catch (Exception closeError) {
                Loggers.TOOL.error("Stdio cleanup after connection failure failed: {}", closeError.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean disconnect(float timeout) {
        if (disconnected) {
            Loggers.TOOL.info("Stdio client disconnected successfully");
            return true;
        }
        try {
            if (session != null) {
                session.close(timeout);
            }
            Loggers.TOOL.info("Stdio client disconnected successfully");
            disconnected = true;
            return true;
        } catch (RuntimeException error) {
            if (session != null) {
                session.closeClientFallback();
            }
            Loggers.TOOL.info("Stdio client disconnected successfully");
            disconnected = true;
            return true;
        } catch (Exception error) {
            Loggers.TOOL.error("Stdio disconnection failed: {}", error.getMessage());
            return false;
        } finally {
            session = null;
        }
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        StdioSession currentSession = requireSession();
        try {
            List<ToolDefinition> tools = currentSession.listTools(timeout);
            List<Object> toolCards = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                toolCards.add(McpToolCard.builder()
                        .name(tool.name())
                        .serverName(name)
                        .serverId(config.getServerId())
                        .description(tool.description())
                        .inputParams(tool.inputSchema())
                        .build());
            }
            Loggers.TOOL.info("Retrieved {} tools from Stdio server", toolCards.size());
            return toolCards;
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to list tools via Stdio: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        StdioSession currentSession = requireSession();
        try {
            Map<String, Object> callArguments = arguments != null ? new LinkedHashMap<>(arguments) : new LinkedHashMap<>();
            Loggers.TOOL.info("Calling tool '{}' via Stdio with arguments: {}", toolName, callArguments);
            Object toolResult = currentSession.callTool(toolName, callArguments, timeout);
            Object resultContent = McpBase.extractMcpToolResultContent(toolResult);
            Loggers.TOOL.info("Tool '{}' call completed via Stdio", toolName);
            return resultContent;
        } catch (Exception error) {
            Loggers.TOOL.error("Tool call failed via Stdio: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        List<Object> tools = listTools(timeout);
        for (Object tool : tools) {
            if (tool instanceof McpToolCard card && Objects.equals(card.getName(), toolName)) {
                Loggers.TOOL.debug("Found tool info for '{}' via Stdio", toolName);
                return Optional.of(card);
            }
        }
        Loggers.TOOL.warning("Tool '{}' not found via Stdio", toolName);
        return Optional.empty();
    }

    @Override
    public List<Object> listResources(float timeout) throws Exception {
        StdioSession currentSession = requireSession();
        try {
            return currentSession.listResources(timeout);
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to list resources via Stdio: {}", error.getMessage());
            throw error;
        }
    }

    @Override
    public Object readResource(String uri, float timeout) throws Exception {
        StdioSession currentSession = requireSession();
        try {
            return currentSession.readResource(uri, timeout);
        } catch (Exception error) {
            Loggers.TOOL.error("Failed to read resource '{}' via Stdio: {}", uri, error.getMessage());
            throw error;
        }
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    StdioServerParameters buildServerParameters() {
        String handler = stringValue(params.getOrDefault("encoding_error_handler", "strict"));
        if (!VALID_ENCODING_HANDLERS.contains(handler)) {
            handler = "strict";
        }
        return new StdioServerParameters(
                stringOrNull(params.get("command")),
                stringList(params.get("args")),
                stringMap(params.get("env")),
                stringOrNull(params.get("cwd")),
                handler
        );
    }

    private StdioSession requireSession() {
        if (session == null) {
            throw new RuntimeException("Not connected to Stdio server");
        }
        return session;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static List<Object> objectList(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Factory for creating a stdio MCP session.
     *
     * <p>Mirrors Python's {@code stdio_client(params)} and {@code ClientSession(...)} setup in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    @FunctionalInterface
    interface SessionFactory {
        StdioSession open(StdioServerParameters parameters) throws Exception;
    }

    /**
     * MCP session operations used by {@link StdioClient}.
     *
     * <p>Mirrors Python's {@code ClientSession} methods used in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    interface StdioSession {
        void initialize(float timeout) throws Exception;

        List<ToolDefinition> listTools(float timeout) throws Exception;

        Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception;

        List<Object> listResources(float timeout) throws Exception;

        Object readResource(String uri, float timeout) throws Exception;

        void close(float timeout) throws Exception;

        default void closeClientFallback() {
            // Python calls the client context manager's __aexit__ in selected close failures.
        }
    }

    /**
     * Stdio server parameters.
     *
     * <p>Mirrors Python's {@code StdioServerParameters} creation in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    record StdioServerParameters(String command, List<String> args, Map<String, String> env, String cwd,
                                  String encodingErrorHandler) {
    }

    /**
     * Tool definition returned from `tools/list`.
     *
     * <p>Mirrors Python's MCP tool objects consumed by {@code StdioClient.list_tools()} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    }

    /**
     * Content-length framed JSON-RPC stdio session.
     *
     * <p>Mirrors Python's stdio MCP session usage in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    static final class JsonRpcProcessSession implements StdioSession {
        private static final Duration DEFAULT_TERMINATION_WAIT = Duration.ofSeconds(1);

        private final Process process;
        private final BufferedInputStream stdout;
        private final BufferedOutputStream stdin;
        private final AtomicLong requestCounter = new AtomicLong();

        private JsonRpcProcessSession(Process process) {
            this.process = process;
            this.stdout = new BufferedInputStream(process.getInputStream());
            this.stdin = new BufferedOutputStream(process.getOutputStream());
        }

        static JsonRpcProcessSession open(StdioServerParameters parameters) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine(parameters));
            if (!parameters.env().isEmpty()) {
                processBuilder.environment().putAll(parameters.env());
            }
            if (parameters.cwd() != null) {
                processBuilder.directory(new File(parameters.cwd()));
            }
            return new JsonRpcProcessSession(processBuilder.start());
        }

        @Override
        public void initialize(float timeout) throws Exception {
            request("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "clientInfo", Map.of("name", "agent-core-java", "version", "0.1.14"),
                    "capabilities", Map.of()
            ), timeout);
        }

        @Override
        public List<ToolDefinition> listTools(float timeout) throws Exception {
            Map<String, Object> result = request("tools/list", Map.of(), timeout);
            List<ToolDefinition> tools = new ArrayList<>();
            for (Object rawTool : objectList(result.get("tools"))) {
                Map<String, Object> tool = objectMap(rawTool);
                tools.add(new ToolDefinition(
                        stringValue(tool.get("name")),
                        stringValue(tool.get("description")),
                        objectMap(tool.get("inputSchema"))
                ));
            }
            return tools;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
            return request("tools/call", Map.of(
                    "name", toolName,
                    "arguments", arguments != null ? arguments : Map.of()
            ), timeout);
        }

        @Override
        public List<Object> listResources(float timeout) throws Exception {
            return objectList(request("resources/list", Map.of(), timeout).get("resources"));
        }

        @Override
        public Object readResource(String uri, float timeout) throws Exception {
            return request("resources/read", Map.of("uri", uri), timeout).get("contents");
        }

        @Override
        public void close(float timeout) throws Exception {
            stdin.close();
            stdout.close();
            process.destroy();
            process.waitFor(DEFAULT_TERMINATION_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void closeClientFallback() {
            process.destroy();
        }

        private synchronized Map<String, Object> request(String method, Map<String, Object> requestParams,
                                                         float timeout) throws Exception {
            long requestId = requestCounter.incrementAndGet();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", requestId);
            body.put("method", method);
            body.put("params", requestParams);
            writeFrame(MAPPER.writeValueAsBytes(body));

            while (true) {
                Map<String, Object> frame = readFrame();
                Object responseId = frame.get("id");
                if (!(responseId instanceof Number number) || number.longValue() != requestId) {
                    continue;
                }
                if (frame.containsKey("error")) {
                    throw new IllegalStateException(String.valueOf(frame.get("error")));
                }
                return objectMap(frame.get("result"));
            }
        }

        private void writeFrame(byte[] jsonBytes) throws IOException {
            String header = "Content-Length: " + jsonBytes.length + "\r\n\r\n";
            stdin.write(header.getBytes(StandardCharsets.UTF_8));
            stdin.write(jsonBytes);
            stdin.flush();
        }

        private Map<String, Object> readFrame() throws IOException {
            int contentLength = -1;
            String line;
            while (!(line = readHeaderLine()).isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            if (contentLength < 0) {
                throw new IllegalStateException("Missing Content-Length in stdio MCP response");
            }
            byte[] body = stdout.readNBytes(contentLength);
            return MAPPER.readValue(body, new TypeReference<>() {
            });
        }

        private String readHeaderLine() throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int current;
            while ((current = stdout.read()) != -1) {
                if (current == '\r') {
                    int next = stdout.read();
                    if (next == '\n') {
                        break;
                    }
                    buffer.write(current);
                    if (next != -1) {
                        buffer.write(next);
                    }
                    continue;
                }
                buffer.write(current);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        private static List<String> commandLine(StdioServerParameters parameters) {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(parameters.command());
            commandLine.addAll(parameters.args());
            return commandLine;
        }
    }
}
