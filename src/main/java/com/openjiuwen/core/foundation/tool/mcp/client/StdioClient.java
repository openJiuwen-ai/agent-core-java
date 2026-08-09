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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private static final Set<String> DEFAULT_ALLOWED_COMMAND_NAMES = Set.of(
            "java", "java.exe", "python", "python.exe", "python3", "python3.exe");

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

    StdioServerParameters buildServerParameters() throws IOException {
        String handler = stringValue(params.getOrDefault("encoding_error_handler", "strict"));
        if (!VALID_ENCODING_HANDLERS.contains(handler)) {
            handler = "strict";
        }
        String command = resolveAllowedCommand(config);
        return new StdioServerParameters(
                command,
                stringList(params.get("args")),
                stringMap(params.get("env")),
                stringOrNull(params.get("cwd")),
                handler
        );
    }

    static String resolveAllowedCommand(McpServerConfig config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("MCP stdio config must not be null.");
        }

        String declaredCommand = config.getServerPath() == null ? "" : config.getServerPath().trim();
        Object configuredCommand = config.getParams() == null ? null : config.getParams().get("command");
        String requestedCommand = configuredCommand == null
                ? declaredCommand
                : String.valueOf(configuredCommand).trim();
        if (requestedCommand.isBlank()) {
            throw new IllegalArgumentException("MCP stdio command must not be blank.");
        }

        if (declaredCommand.isBlank()) {
            return resolveDefaultAllowedCommand(requestedCommand).toString();
        }

        ResolvedCommand allowedExecutable = resolveCommand(declaredCommand);
        ResolvedCommand requestedExecutable = resolveCommand(requestedCommand);
        if (!requestedExecutable.realPath().equals(allowedExecutable.realPath())) {
            throw new SecurityException("MCP stdio command is not in the configured allowlist.");
        }
        return allowedExecutable.launchPath().toString();
    }

    private static Path resolveDefaultAllowedCommand(String requestedCommand) throws IOException {
        Path requestedPath = Path.of(requestedCommand);
        Path fileName = requestedPath.getFileName();
        String commandName = fileName == null ? "" : fileName.toString();
        if (!DEFAULT_ALLOWED_COMMAND_NAMES.contains(commandName)) {
            throw new SecurityException("MCP stdio command is not in the default allowlist.");
        }

        ResolvedCommand requestedExecutable = resolveCommand(requestedCommand);
        if (requestedPath.isAbsolute()) {
            return requestedExecutable.launchPath();
        }
        if ("java".equals(commandName) || "java.exe".equals(commandName)) {
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", commandName);
            if (Files.isRegularFile(javaExecutable) && Files.isExecutable(javaExecutable)
                    && requestedExecutable.realPath().equals(validateExecutable(javaExecutable))) {
                return requestedExecutable.launchPath();
            }
        }

        ResolvedCommand pathExecutable = resolveCommand(commandName);
        if (!requestedExecutable.realPath().equals(pathExecutable.realPath())) {
            throw new SecurityException("MCP stdio command does not match the trusted PATH executable.");
        }
        return requestedExecutable.launchPath();
    }

    private static ResolvedCommand resolveCommand(String command) throws IOException {
        if (command.indexOf('\0') >= 0) {
            throw new SecurityException("MCP stdio command contains an invalid character.");
        }
        Path commandPath = Path.of(command);
        if (commandPath.isAbsolute() || commandPath.getNameCount() > 1) {
            Path launchPath = commandPath.toAbsolutePath().normalize();
            return new ResolvedCommand(launchPath, validateExecutable(launchPath));
        }

        String systemPath = System.getenv("PATH");
        if (systemPath == null || systemPath.isBlank()) {
            throw new SecurityException("PATH is unavailable for resolving MCP stdio command: " + command);
        }
        for (String directory : systemPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory).resolve(command).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return new ResolvedCommand(candidate, validateExecutable(candidate));
            }
        }
        throw new SecurityException("MCP stdio command is not an executable file: " + command);
    }

    private static Path validateExecutable(Path executable) throws IOException {
        Path realExecutable = executable.toRealPath();
        if (!Files.isRegularFile(realExecutable) || !Files.isExecutable(realExecutable)) {
            throw new SecurityException("MCP stdio command is not an executable file: " + executable);
        }
        return realExecutable;
    }

    private record ResolvedCommand(Path launchPath, Path realPath) {
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
     * Newline-delimited JSON-RPC stdio session with content-length read compatibility.
     *
     * <p>Mirrors Python's stdio MCP session usage in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.</p>
     */
    static final class JsonRpcProcessSession implements StdioSession {
        private static final Duration DEFAULT_TERMINATION_WAIT = Duration.ofSeconds(1);
        static final int STDERR_TAIL_CHARS = 8192;
        private static final int STDERR_DRAIN_CHARS = 1024;
        private static final Duration STDERR_DRAIN_JOIN_WAIT = Duration.ofMillis(200);

        private final Process process;
        private final BufferedInputStream stdout;
        private final BufferedInputStream stderr;
        private final BufferedOutputStream stdin;
        private final Duration terminationWait;
        private final StringBuilder stderrTail = new StringBuilder();
        private final Object closeLock = new Object();
        private final AtomicLong requestCounter = new AtomicLong();
        private final Thread stderrDrainThread;
        private boolean closed;

        JsonRpcProcessSession(Process process) {
            this(process, DEFAULT_TERMINATION_WAIT);
        }

        JsonRpcProcessSession(Process process, Duration terminationWait) {
            this.process = process;
            this.stdout = new BufferedInputStream(process.getInputStream());
            this.stderr = new BufferedInputStream(process.getErrorStream());
            this.stdin = new BufferedOutputStream(process.getOutputStream());
            this.terminationWait = terminationWait;
            this.stderrDrainThread = startStderrDrain();
        }

        static JsonRpcProcessSession open(StdioServerParameters parameters) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine(parameters));
            processBuilder.redirectErrorStream(false);
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
            notification("notifications/initialized");
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
            cleanupProcess();
        }

        @Override
        public void closeClientFallback() {
            cleanupProcess();
        }

        String stderrTail() {
            synchronized (stderrTail) {
                return stderrTail.toString();
            }
        }

        boolean stderrDrainAlive() {
            return stderrDrainThread.isAlive();
        }

        private void cleanupProcess() {
            synchronized (closeLock) {
                if (closed) {
                    return;
                }
                closed = true;
                closeQuietly(stdin);
                closeQuietly(stdout);
                process.destroy();
                if (!waitForExit() && process.isAlive()) {
                    process.destroyForcibly();
                    waitForExit();
                }
                closeQuietly(stderr);
                joinStderrDrain();
            }
        }

        private boolean waitForExit() {
            try {
                return process.waitFor(terminationWait.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return !process.isAlive();
            }
        }

        private static void closeQuietly(AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best-effort process cleanup should continue after stream close failures.
            }
        }

        private void joinStderrDrain() {
            if (Thread.currentThread() == stderrDrainThread) {
                return;
            }
            try {
                stderrDrainThread.join(STDERR_DRAIN_JOIN_WAIT.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private Thread startStderrDrain() {
            Thread thread = new Thread(this::drainStderr, "stdio-mcp-stderr-drain");
            thread.setDaemon(true);
            thread.start();
            return thread;
        }

        private void drainStderr() {
            try (InputStreamReader streamReader =
                         new InputStreamReader(stderr, StandardCharsets.UTF_8)) {
                char[] buffer = new char[STDERR_DRAIN_CHARS];
                int charsRead;
                while ((charsRead = streamReader.read(buffer)) != -1) {
                    appendStderrText(buffer, charsRead);
                }
            } catch (IOException ignored) {
                // Stderr is diagnostic-only; lifecycle cleanup should not fail because draining ended.
            }
        }

        private void appendStderrText(char[] buffer, int length) {
            synchronized (stderrTail) {
                stderrTail.append(buffer, 0, length);
                int excess = stderrTail.length() - STDERR_TAIL_CHARS;
                if (excess > 0) {
                    stderrTail.delete(0, excess);
                }
            }
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

            if (timeout > 0) {
                return readResponseWithTimeout(requestId, timeout);
            }
            return readResponse(requestId);
        }

        private synchronized void notification(String method) throws IOException {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            writeFrame(MAPPER.writeValueAsBytes(body));
        }

        private Map<String, Object> readResponseWithTimeout(long requestId, float timeout) throws Exception {
            ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "stdio-mcp-response-reader-" + requestId);
                thread.setDaemon(true);
                return thread;
            });
            Future<Map<String, Object>> response = executor.submit(() -> readResponse(requestId));
            try {
                return response.get(timeoutMillis(timeout), TimeUnit.MILLISECONDS);
            } catch (TimeoutException error) {
                response.cancel(true);
                cleanupProcess();
                throw new IOException("Timed out waiting for stdio MCP response id " + requestId
                        + diagnosticSuffix(), error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                response.cancel(true);
                cleanupProcess();
                throw new IOException("Interrupted waiting for stdio MCP response id " + requestId
                        + diagnosticSuffix(), error);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new RuntimeException(cause != null ? cause : error);
            } finally {
                executor.shutdownNow();
            }
        }

        private String diagnosticSuffix() {
            String tail = stderrTail();
            if (tail == null || tail.isBlank()) {
                return "";
            }
            return "; stderr tail: " + tail;
        }

        private static long timeoutMillis(float timeout) {
            return Math.max(1L, (long) Math.ceil(timeout * 1000.0D));
        }

        private Map<String, Object> readResponse(long requestId) throws IOException {
            while (true) {
                Map<String, Object> frame = readFrame();
                Object method = frame.get("method");
                if (method instanceof String methodName) {
                    handleServerMessage(frame, methodName);
                    continue;
                }
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

        private void handleServerMessage(Map<String, Object> frame, String method) throws IOException {
            if (!frame.containsKey("id")) {
                return;
            }
            Object id = frame.get("id");
            if ("ping".equals(method)) {
                writeResponse(id, Map.of());
                return;
            }
            writeError(id, -32601, "Method not found: " + method);
        }

        private void writeResponse(Object id, Map<String, Object> result) throws IOException {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", id);
            body.put("result", result);
            writeFrame(MAPPER.writeValueAsBytes(body));
        }

        private void writeError(Object id, int code, String message) throws IOException {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", id);
            body.put("error", Map.of("code", code, "message", message));
            writeFrame(MAPPER.writeValueAsBytes(body));
        }

        private void writeFrame(byte[] jsonBytes) throws IOException {
            stdin.write(jsonBytes);
            stdin.write('\n');
            stdin.flush();
        }

        private Map<String, Object> readFrame() throws IOException {
            String line = readLine();
            while (line.isEmpty()) {
                line = readLine();
            }
            if (!line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                return MAPPER.readValue(line, new TypeReference<>() {
                });
            }
            return readContentLengthFrame(line);
        }

        private Map<String, Object> readContentLengthFrame(String firstHeaderLine) throws IOException {
            int contentLength = -1;
            String line = firstHeaderLine;
            while (!line.isEmpty()) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
                line = readLine();
            }
            if (contentLength < 0) {
                throw new IllegalStateException("Missing Content-Length in stdio MCP response");
            }
            byte[] body = stdout.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new IOException("Stdio MCP response stream closed before reading full body");
            }
            return MAPPER.readValue(body, new TypeReference<>() {
            });
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int current;
            while ((current = stdout.read()) != -1) {
                if (current == '\n') {
                    break;
                }
                buffer.write(current);
            }
            if (current == -1 && buffer.size() == 0) {
                throw new IOException("Stdio MCP response stream closed");
            }
            String line = buffer.toString(StandardCharsets.UTF_8);
            if (line.endsWith("\r")) {
                return line.substring(0, line.length() - 1);
            }
            return line;
        }

        private static List<String> commandLine(StdioServerParameters parameters) {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(parameters.command());
            commandLine.addAll(parameters.args());
            return commandLine;
        }
    }
}
