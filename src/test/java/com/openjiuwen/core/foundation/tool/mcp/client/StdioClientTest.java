/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code StdioClient} behavior in
 * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.
 */
class StdioClientTest {

    @Test
    void buildServerParametersUsesParamsAndNormalizesEncodingHandler() {
        StdioClient client = new StdioClient(config(Map.of(
                "command", "python",
                "args", List.of("-m", "server"),
                "env", Map.of("A", "B"),
                "cwd", "workspace",
                "encoding_error_handler", "drop"
        )));

        StdioClient.StdioServerParameters parameters = client.buildServerParameters();

        assertEquals("python", parameters.command());
        assertEquals(List.of("-m", "server"), parameters.args());
        assertEquals(Map.of("A", "B"), parameters.env());
        assertEquals("workspace", parameters.cwd());
        assertEquals("strict", parameters.encodingErrorHandler());
    }

    @Test
    void connectInitializesSessionAndDisconnectIsIdempotent() throws Exception {
        FakeSession session = new FakeSession();
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> session);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertTrue(session.initialized);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(session.closed);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertEquals(1, session.closeCalls);
    }

    @Test
    void connectReturnsFalseWhenSessionCannotOpen() {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> {
                    throw new IllegalStateException("boom");
                });

        assertFalse(client.connect(1, McpServerConfig.NO_TIMEOUT));
    }

    @Test
    void operationsRequireConnectedSession() {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> new FakeSession());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.listTools(McpServerConfig.NO_TIMEOUT));

        assertEquals("Not connected to Stdio server", error.getMessage());
    }

    @Test
    void listToolsMapsSessionToolsToMcpCards() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new StdioClient.ToolDefinition("lookup", "Lookup",
                Map.of("type", "object")));
        StdioClient client = connectedClient(session);

        List<Object> tools = client.listTools(McpServerConfig.NO_TIMEOUT);

        assertEquals(1, tools.size());
        McpToolCard card = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("lookup", card.getName());
        assertEquals("stdio-server", card.getServerName());
        assertEquals("srv-stdio", card.getServerId());
        assertEquals("Lookup", card.getDescription());
        assertEquals(Map.of("type", "object"), card.getInputParams());
    }

    @Test
    void callToolExtractsResultContentAndPassesArguments() throws Exception {
        FakeSession session = new FakeSession();
        session.callResult = Map.of("content", List.of(Map.of("text", "ok")));
        StdioClient client = connectedClient(session);

        Object result = client.callTool("lookup", Map.of("query", "hello"), McpServerConfig.NO_TIMEOUT);

        assertEquals("ok", result);
        assertEquals("lookup", session.lastToolName);
        assertEquals(Map.of("query", "hello"), session.lastArguments);
    }

    @Test
    void getToolInfoFindsMatchingToolOrReturnsEmpty() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new StdioClient.ToolDefinition("lookup", "Lookup", Map.of()));
        StdioClient client = connectedClient(session);

        assertTrue(client.getToolInfo("lookup", McpServerConfig.NO_TIMEOUT).isPresent());
        assertTrue(client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT).isEmpty());
    }

    @Test
    void resourcesAreDelegatedToSession() throws Exception {
        FakeSession session = new FakeSession();
        session.resources = List.of(Map.of("uri", "file://a"));
        session.contents = List.of(Map.of("text", "body"));
        StdioClient client = connectedClient(session);

        assertEquals(session.resources, client.listResources(McpServerConfig.NO_TIMEOUT));
        assertSame(session.contents, client.readResource("file://a", McpServerConfig.NO_TIMEOUT));
        assertEquals("file://a", session.lastResourceUri);
    }

    @Test
    void jsonRpcProcessSessionForcesProcessExitWhenDestroyDoesNotStopIt() throws Exception {
        FakeProcess process = new FakeProcess(false, true);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));

        session.close(McpServerConfig.NO_TIMEOUT);
        session.close(McpServerConfig.NO_TIMEOUT);

        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.destroyForciblyCalls);
    }

    @Test
    void jsonRpcProcessSessionDrainsStderrAndKeepsTail() throws Exception {
        FakeProcess process = new FakeProcess(true, true);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));

        process.writeStderr("first\nsecond\n");

        assertTrue(stderrTailContains(session, "second"));

        session.close(McpServerConfig.NO_TIMEOUT);
    }

    @Test
    void jsonRpcProcessSessionKeepsBoundedStderrTailForLongOutputWithoutNewlines() throws Exception {
        FakeProcess process = new FakeProcess(true, true);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));
        String droppedPrefix = "dropped-prefix";
        String longPrefix = droppedPrefix + "a".repeat(StdioClient.JsonRpcProcessSession.STDERR_TAIL_CHARS * 2);
        String suffix = "tail-marker";

        process.writeStderr(longPrefix + suffix);

        assertTrue(stderrTailContains(session, suffix));
        String tail = session.stderrTail();
        assertTrue(tail.length() <= StdioClient.JsonRpcProcessSession.STDERR_TAIL_CHARS);
        assertFalse(tail.contains(droppedPrefix));
        assertTrue(tail.endsWith(suffix));

        session.close(McpServerConfig.NO_TIMEOUT);
    }

    @Test
    void jsonRpcProcessSessionClosesStderrDrainWhenProcessRemainsAliveAfterForceDestroy() throws Exception {
        FakeProcess process = new FakeProcess(false, false);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));

        session.close(McpServerConfig.NO_TIMEOUT);

        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.destroyForciblyCalls);
        assertTrue(process.stderrClosed());
        assertFalse(session.stderrDrainAlive());
    }

    @Test
    @Timeout(2)
    void jsonRpcProcessSessionTimesOutWaitingForResponseAndCleansProcess() {
        BlockingInputStream stdout = new BlockingInputStream();
        FakeProcess process = new FakeProcess(false, true, stdout);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));

        IOException error = assertThrows(IOException.class, () -> session.listTools(0.05F));

        assertTrue(error.getMessage().contains("Timed out waiting for stdio MCP response"));
        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.destroyForciblyCalls);
    }

    @Test
    @Timeout(2)
    void jsonRpcProcessSessionInterruptedWhileWaitingForResponseCleansProcess() throws Exception {
        BlockingInputStream stdout = new BlockingInputStream();
        FakeProcess process = new FakeProcess(false, true, stdout);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedFlagRestored = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                session.listTools(5.0F);
            } catch (Throwable error) {
                thrown.set(error);
                interruptedFlagRestored.set(Thread.currentThread().isInterrupted());
            }
        }, "stdio-client-interrupt-test");

        caller.start();
        assertTrue(waitUntil(() -> process.stdinText().contains("\"method\":\"tools/list\""), Duration.ofSeconds(1)));
        caller.interrupt();
        caller.join(1000);

        assertFalse(caller.isAlive());
        assertInstanceOf(IOException.class, thrown.get());
        assertTrue(thrown.get().getMessage().contains("Interrupted waiting for stdio MCP response"));
        assertTrue(interruptedFlagRestored.get());
        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.destroyForciblyCalls);
    }

    @Test
    @Timeout(2)
    void jsonRpcProcessSessionUsesNewlineDelimitedJsonRpcFramesAndSkipsNotifications() throws Exception {
        BlockingInputStream stdout = new BlockingInputStream();
        FakeProcess process = new FakeProcess(false, true, stdout);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));
        stdout.write(jsonLine(Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/message",
                "params", Map.of("level", "info", "data", "noise")
        )));
        stdout.write(jsonLine(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "result", Map.of("tools", List.of(Map.of(
                        "name", "echo",
                        "description", "Echo",
                        "inputSchema", Map.of("type", "object")
                )))
        )));

        List<StdioClient.ToolDefinition> tools = session.listTools(0.5F);

        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        String stdin = process.stdinText();
        assertTrue(stdin.endsWith("\n"));
        assertTrue(stdin.contains("\"method\":\"tools/list\""));
        assertFalse(stdin.contains("Content-Length"));
        session.close(McpServerConfig.NO_TIMEOUT);
    }

    @Test
    @Timeout(2)
    void jsonRpcProcessSessionSendsInitializedNotificationAfterInitializeResponse() throws Exception {
        BlockingInputStream stdout = new BlockingInputStream();
        FakeProcess process = new FakeProcess(true, true, stdout);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));
        stdout.write(jsonLine(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "result", Map.of(
                        "protocolVersion", "2024-11-05",
                        "serverInfo", Map.of("name", "fake", "version", "1.0.0"),
                        "capabilities", Map.of()
                )
        )));

        session.initialize(0.5F);

        String stdin = process.stdinText();
        assertTrue(stdin.contains("\"method\":\"initialize\""));
        assertTrue(stdin.contains("\"method\":\"notifications/initialized\""));
        assertFalse(stdin.substring(stdin.indexOf("\"method\":\"notifications/initialized\"")).contains("\"id\":"));
        session.close(McpServerConfig.NO_TIMEOUT);
    }

    @Test
    @Timeout(2)
    void jsonRpcProcessSessionRespondsToServerPingWhileWaitingForResponse() throws Exception {
        BlockingInputStream stdout = new BlockingInputStream();
        FakeProcess process = new FakeProcess(true, true, stdout);
        StdioClient.JsonRpcProcessSession session =
                new StdioClient.JsonRpcProcessSession(process, Duration.ofMillis(1));
        stdout.write(jsonLine(Map.of(
                "jsonrpc", "2.0",
                "id", 41,
                "method", "ping"
        )));
        stdout.write(jsonLine(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "result", Map.of("tools", List.of())
        )));

        assertTrue(session.listTools(0.5F).isEmpty());

        String stdin = process.stdinText();
        assertTrue(stdin.contains("\"method\":\"tools/list\""));
        assertTrue(stdin.contains("\"id\":41"));
        assertTrue(stdin.contains("\"result\":{}"));
        session.close(McpServerConfig.NO_TIMEOUT);
    }

    private static boolean stderrTailContains(StdioClient.JsonRpcProcessSession session, String expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (session.stderrTail().contains(expected)) {
                return true;
            }
            sleepQuietly(Duration.ofMillis(10));
        }
        return false;
    }

    private static void sleepQuietly(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean waitUntil(Check condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return true;
            }
            sleepQuietly(Duration.ofMillis(10));
        }
        return condition.matches();
    }

    private static StdioClient connectedClient(FakeSession session) {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> session);
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        return client;
    }

    private static byte[] jsonLine(Map<String, Object> frame) throws IOException {
        return (StdioClientTestJson.MAPPER.writeValueAsString(frame) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }

    private static McpServerConfig config(Map<String, Object> params) {
        return new McpServerConfig("srv-stdio", "stdio-server", "ignored", "stdio",
                params, Map.of(), Map.of());
    }

    /**
     * Mirrors Python's fake {@code ClientSession} behavior needed by {@code StdioClient} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.
     */
    private static final class FakeSession implements StdioClient.StdioSession {
        private boolean initialized;
        private boolean closed;
        private int closeCalls;
        private final List<StdioClient.ToolDefinition> tools = new ArrayList<>();
        private Object callResult = Map.of("content", List.of());
        private String lastToolName;
        private Map<String, Object> lastArguments = new LinkedHashMap<>();
        private List<Object> resources = List.of();
        private Object contents;
        private String lastResourceUri;

        @Override
        public void initialize(float timeout) {
            initialized = true;
        }

        @Override
        public List<StdioClient.ToolDefinition> listTools(float timeout) {
            return tools;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            lastToolName = toolName;
            lastArguments = new LinkedHashMap<>(arguments);
            return callResult;
        }

        @Override
        public List<Object> listResources(float timeout) {
            return resources;
        }

        @Override
        public Object readResource(String uri, float timeout) {
            lastResourceUri = uri;
            return contents;
        }

        @Override
        public void close(float timeout) {
            closed = true;
            closeCalls++;
        }
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private final InputStream stdout;
        private final BlockingInputStream stderr = new BlockingInputStream();
        private final boolean exitsOnDestroy;
        private final boolean exitsOnForceDestroy;
        private boolean alive = true;
        private int destroyCalls;
        private int destroyForciblyCalls;

        private FakeProcess(boolean exitsOnDestroy, boolean exitsOnForceDestroy) {
            this(exitsOnDestroy, exitsOnForceDestroy, new ByteArrayInputStream(new byte[0]));
        }

        private FakeProcess(boolean exitsOnDestroy, boolean exitsOnForceDestroy, InputStream stdout) {
            this.exitsOnDestroy = exitsOnDestroy;
            this.exitsOnForceDestroy = exitsOnForceDestroy;
            this.stdout = stdout;
        }

        void writeStderr(String text) {
            stderr.write(text.getBytes(StandardCharsets.UTF_8));
        }

        String stdinText() {
            return stdin.toString(StandardCharsets.UTF_8);
        }

        boolean stderrClosed() {
            return stderr.closed;
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("still alive");
            }
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalls++;
            if (exitsOnDestroy) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalls++;
            if (exitsOnForceDestroy) {
                alive = false;
            }
            return this;
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        void write(byte[] value) {
            for (byte item : value) {
                bytes.add((int) item & 0xff);
            }
        }

        void closeQuietly() {
            closed = true;
            bytes.add(-1);
        }

        @Override
        public void close() {
            closeQuietly();
        }

        @Override
        public int read() throws IOException {
            try {
                Integer value;
                while ((value = bytes.poll(50, TimeUnit.MILLISECONDS)) == null) {
                    if (closed) {
                        return -1;
                    }
                }
                return value;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int first = read();
            if (first == -1) {
                return -1;
            }
            buffer[offset] = (byte) first;
            int count = 1;
            Integer value;
            while (count < length && (value = bytes.poll()) != null) {
                if (value == -1) {
                    closed = true;
                    break;
                }
                buffer[offset + count] = (byte) (value & 0xff);
                count++;
            }
            return count;
        }
    }

    private static final class StdioClientTestJson {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
