/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.foundation.tool.mcp.CountingMcpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.resourcemanager.ToolMgr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * MCP client request-timeout tests: discovery-family RPCs (initialize, tools/list) and the connect
 * guard carry a bounded request timeout — a positive caller timeout is
 * used as-is, any non-positive value (including the
 * {@link McpServerConfig#NO_TIMEOUT} sentinel) falls back to the 30s
 * default on the HTTP and stdio transports. Execution-family RPCs
 * (tools/call, resources/read, resources/list) keep the baseline
 * sentinel semantics — unbounded; the behavioral cases pin
 * tools/call and resources/read against a slow stub (resources/list
 * shares the semantics but has no behavioral pin).
 * <p>
 * The ToolMgr-level connect guard (baseline connectClient behavior) is
 * locked in as a regression anchor for the client-layer hardening.
 */
@DisplayName("MCP discovery request timeouts (client layer)")
@Timeout(60)
class McpDiscoveryTimeoutTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final String PYTHON_STUB = """
            import json
            import sys
            import time

            flood = len(sys.argv) > 1 and sys.argv[1] == "flood"
            slow_call = len(sys.argv) > 1 and sys.argv[1] == "slow-call"
            while True:
                line = sys.stdin.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue
                try:
                    msg = json.loads(line)
                except ValueError:
                    continue
                method = msg.get("method", "")
                if method == "initialize":
                    reply = {"jsonrpc": "2.0", "id": msg.get("id"), "result": {
                        "protocolVersion": "2024-11-05", "capabilities": {},
                        "serverInfo": {"name": "stdio-stub", "version": "1.0"}}}
                    sys.stdout.write(json.dumps(reply) + "\\n")
                    sys.stdout.flush()
                elif method == "tools/list" and flood:
                    # Notification flood: frames keep arriving but the request
                    # is never answered - the request-loop deadline must fire.
                    while True:
                        sys.stdout.write(json.dumps(
                            {"jsonrpc": "2.0", "method": "progress", "params": {}}) + "\\n")
                        sys.stdout.flush()
                        time.sleep(0.05)
                elif method == "tools/list":
                    # Hanging discovery: never answer, the client must hit
                    # its read deadline instead of waiting forever.
                    time.sleep(3600.0)
                elif method == "tools/call":
                    # Slow execution: the sentinel must wait for completion
                    # (baseline unbounded execution), a positive timeout
                    # must interrupt near its deadline.
                    if slow_call:
                        time.sleep(2.0)
                    reply = {"jsonrpc": "2.0", "id": msg.get("id"), "result": {
                        "content": [{"type": "text", "text": "slow-tool-ok"}]}}
                    sys.stdout.write(json.dumps(reply) + "\\n")
                    sys.stdout.flush()
                elif method == "resources/read":
                    if slow_call:
                        time.sleep(2.0)
                    reply = {"jsonrpc": "2.0", "id": msg.get("id"), "result": {
                        "contents": [{"uri": "stub://slow", "text": "slow-resource-ok"}]}}
                    sys.stdout.write(json.dumps(reply) + "\\n")
                    sys.stdout.flush()
            """;

    private com.sun.net.httpserver.HttpServer server;

    /** Temp directories holding the stdio python stubs, cleaned per test. */
    private final List<Path> stubDirs = new ArrayList<>();

    /** ToolMgr recording the counting clients it creates. */
    static final class RecordingToolMgr extends ToolMgr {
        final List<CountingMcpClient> clients = new CopyOnWriteArrayList<>();

        @Override
        protected McpClient createClient(McpServerConfig config) {
            CountingMcpClient client = new CountingMcpClient();
            clients.add(client);
            return client;
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        for (Path stubDir : stubDirs) {
            try (Stream<Path> paths = Files.walk(stubDir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // best-effort cleanup of the stdio stub directory
                            }
                        });
            } catch (IOException e) {
                // best-effort cleanup of the stdio stub directory
            }
        }
        stubDirs.clear();
    }

    @Test
    @DisplayName("HTTP request timeout set for positive, fallback for non-positive")
    void httpRequestTimeouts_positiveExactAndNonPositiveFallback() throws Exception {
        List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        server = startRespondingServer();
        McpServerConfig config = captureConfig(requests);

        StreamableHttpClient client = new StreamableHttpClient(config);
        assertThat(client.connect(1, McpServerConfig.NO_TIMEOUT)).isTrue();
        client.listTools(0.5f);
        client.listTools(McpServerConfig.NO_TIMEOUT);
        client.listTools(0f);
        client.listTools(-7f);
        client.listTools();

        // Request order on this single-threaded script: initialize,
        // notifications/initialized, then the five listTools calls.
        assertThat(requests).hasSize(7);
        assertThat(requests.get(0).timeout()).as("connect guard: sentinel initialize gets the fallback")
                .contains(Duration.ofSeconds(30L));
        assertThat(requests.get(2).timeout()).as("positive timeout used as-is")
                .contains(Duration.ofMillis(500L));
        assertThat(requests.get(3).timeout()).as("NO_TIMEOUT sentinel falls back")
                .contains(Duration.ofSeconds(30L));
        assertThat(requests.get(4).timeout()).as("zero falls back").contains(Duration.ofSeconds(30L));
        assertThat(requests.get(5).timeout()).as("negative falls back").contains(Duration.ofSeconds(30L));
        assertThat(requests.get(6).timeout()).as("no-arg default falls back")
                .contains(Duration.ofSeconds(30L));
        assertThat(requests.get(2).headers().firstValue("X-Test-Auth"))
                .as("configured auth headers ride on the bounded request").contains("secret-token");
    }

    @Test
    @DisplayName("Positive timeout interrupts a delayed HTTP response")
    void httpPositiveTimeout_interruptsDelayedResponse() throws Exception {
        server = startDelayedToolsListServer();
        StreamableHttpClient client = new StreamableHttpClient(plainConfig());

        assertThat(client.connect(0, 5f)).isTrue();
        assertThatThrownBy(() -> client.listTools(0.5f))
                .as("request timeout is enforced against a slow server")
                .isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    @DisplayName("HTTP client keeps the 10s constructor connect-timeout default")
    void httpClientDefaultsToTenSecondConnectTimeout() {
        McpServerConfig config = McpServerConfig.builder().serverName("default-connect-timeout-server")
                .serverPath("http://127.0.0.1:9/mcp").clientType("streamable-http").build();
        StreamableHttpClient client = new StreamableHttpClient(config);

        assertThat(client.httpClient.connectTimeout())
                .as("constructor connect timeout falls back to 10s when unconfigured")
                .contains(Duration.ofSeconds(10L));
    }

    @Test
    @DisplayName("Stdio deadline interrupts a hanging discovery")
    void stdioDeadlineInterruptsHangingDiscovery() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig());
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            long startedAt = System.currentTimeMillis();
            assertThatThrownBy(() -> client.listTools(1f))
                    .as("positive deadline interrupts the hanging discovery")
                    .isInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(System.currentTimeMillis() - startedAt).as("interrupt happened near the deadline")
                    .isLessThan(5000L);
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio NO_TIMEOUT no longer skips the deadline")
    void stdioNoTimeoutFallsBackToBoundedDeadline() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig());
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(40L), () -> assertThatThrownBy(
                    () -> client.listTools(McpServerConfig.NO_TIMEOUT))
                    .as("sentinel falls back to a bounded deadline instead of waiting forever")
                    .isInstanceOf(java.net.SocketTimeoutException.class));
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio connect with a positive timeout succeeds against the stub")
    void stdioConnectWithPositiveTimeoutSucceeds() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig());
        try {
            assertThat(client.connect(0, 5f)).as("initialize completes within the positive deadline").isTrue();
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio request-loop deadline fires under a notification flood")
    void stdioRequestLoopDeadline_interruptsNotificationFlood() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig("flood"));
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            long startedAt = System.currentTimeMillis();
            assertThatThrownBy(() -> client.listTools(1f))
                    .as("unanswered request under a frame flood is cut by the request-loop deadline")
                    .isInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(System.currentTimeMillis() - startedAt).as("interrupt happened near the deadline")
                    .isLessThan(5000L);
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio execution sentinel stays unbounded against a slow tool call")
    void stdioCallToolSentinel_runsSlowToolToCompletion() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig("slow-call"));
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            long startedAt = System.currentTimeMillis();
            Object result = client.callTool("slow", Map.of(), McpServerConfig.NO_TIMEOUT);
            long elapsed = System.currentTimeMillis() - startedAt;
            assertThat(result).as("sentinel execution waits for the slow tool and returns its payload")
                    .isEqualTo("slow-tool-ok");
            assertThat(elapsed).as("the slow tool actually ran to completion (no client-side cap)")
                    .isGreaterThanOrEqualTo(1900L)
                    .isLessThan(10_000L);
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio execution sentinel stays unbounded against a slow resource read")
    void stdioReadResourceSentinel_runsSlowReadToCompletion() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig("slow-call"));
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            long startedAt = System.currentTimeMillis();
            List<Object> contents = client.readResource("stub://slow", McpServerConfig.NO_TIMEOUT);
            long elapsed = System.currentTimeMillis() - startedAt;
            assertThat(String.valueOf(contents))
                    .as("sentinel execution waits for the slow resource read").contains("slow-resource-ok");
            assertThat(elapsed).as("the slow read actually ran to completion (no client-side cap)")
                    .isGreaterThanOrEqualTo(1900L)
                    .isLessThan(10_000L);
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio positive execution timeout interrupts a slow tool call")
    void stdioCallToolPositiveTimeout_interruptsSlowTool() throws Exception {
        assumePython3Available();
        StdioClient client = new StdioClient(stdioStubConfig("slow-call"));
        try {
            assertThat(client.connect(0, 5f)).isTrue();
            long startedAt = System.currentTimeMillis();
            assertThatThrownBy(() -> client.callTool("slow", Map.of(), 1f))
                    .as("positive execution timeout interrupts the slow tool call")
                    .isInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(System.currentTimeMillis() - startedAt).as("interrupt happened near the deadline")
                    .isBetween(900L, 3000L);
        } finally {
            client.disconnect(1f);
        }
    }

    @Test
    @DisplayName("Stdio deadline states (baseline sentinel, discovery-side fallback)")
    void stdioDeadlineStates_positiveExactAndNonPositiveFallback() throws Exception {
        long beforePositive = System.currentTimeMillis();
        long positiveDeadline = invokeComputeDeadlineMs(1.5f);
        assertThat(positiveDeadline - beforePositive).as("positive timeout computes an exact deadline")
                .isBetween(1400L, 2000L);

        assertThat(invokeComputeDeadlineMs(McpServerConfig.NO_TIMEOUT))
                .as("NO_TIMEOUT sentinel keeps baseline semantics: no deadline enforcement on execution")
                .isZero();
        assertThat(invokeComputeDeadlineMs(0f) - System.currentTimeMillis())
                .as("zero falls back to the 30s deadline (baseline branch)").isBetween(25_000L, 35_000L);
        assertThat(invokeComputeDeadlineMs(-7f) - System.currentTimeMillis())
                .as("negative falls back to the 30s deadline (baseline branch)").isBetween(25_000L, 35_000L);

        assertThat(invokeResolveDiscoveryTimeout(1.5f)).as("discovery resolver passes positive values through")
                .isEqualTo(1.5f);
        assertThat(invokeResolveDiscoveryTimeout(McpServerConfig.NO_TIMEOUT))
                .as("discovery resolver bounds the sentinel with the 30s default").isEqualTo(30.0f);
        assertThat(invokeResolveDiscoveryTimeout(0f))
                .as("discovery resolver bounds zero with the 30s default").isEqualTo(30.0f);
        assertThat(invokeResolveDiscoveryTimeout(-7f))
                .as("discovery resolver bounds negative values with the 30s default").isEqualTo(30.0f);
    }

    @Test
    @DisplayName("ToolMgr connect guard matches the baseline states")
    void toolMgrConnectTimeoutGuard_matchesBaselineStates() throws Exception {
        RecordingToolMgr mgr = new RecordingToolMgr();
        McpServerConfig positive = pathConfig("guard-positive");
        positive.setConnectTimeoutSeconds(7.0);
        McpServerConfig sentinel = pathConfig("guard-sentinel");
        sentinel.setConnectTimeoutSeconds((double) McpServerConfig.NO_TIMEOUT);
        McpServerConfig zero = pathConfig("guard-zero");
        zero.setConnectTimeoutSeconds(0.0);

        mgr.addToolServer(positive, null);
        mgr.addToolServer(sentinel, null);
        mgr.addToolServer(zero, null);
        mgr.addToolServer(pathConfig("guard-unset"), null);

        assertThat(mgr.clients.get(0).connectTimeouts()).as("positive value passed through").containsExactly(7.0f);
        assertThat(mgr.clients.get(1).connectTimeouts()).as("NO_TIMEOUT falls back").containsExactly(30.0f);
        assertThat(mgr.clients.get(2).connectTimeouts()).as("zero falls back").containsExactly(30.0f);
        assertThat(mgr.clients.get(3).connectTimeouts()).as("unconfigured falls back").containsExactly(30.0f);
    }

    // ========== Fixtures and helpers ==========

    private static long invokeComputeDeadlineMs(float timeout) throws Exception {
        Method method = StdioClient.class.getDeclaredMethod("computeDeadlineMs", float.class);
        method.setAccessible(true);
        Object result = method.invoke(null, timeout);
        if (!(result instanceof Long deadline)) {
            throw new IllegalStateException("computeDeadlineMs did not return a Long");
        }
        return deadline;
    }

    private static float invokeResolveDiscoveryTimeout(float timeout) throws Exception {
        Method method = StdioClient.class.getDeclaredMethod("resolveDiscoveryTimeout", float.class);
        method.setAccessible(true);
        Object result = method.invoke(null, timeout);
        if (!(result instanceof Float resolvedTimeout)) {
            throw new IllegalStateException("resolveDiscoveryTimeout did not return a Float");
        }
        return resolvedTimeout;
    }

    private McpServerConfig captureConfig(List<HttpRequest> requests) throws Exception {
        HttpClient recording = recordingHttpClient(requests);
        return McpServerConfig.builder().serverName("capture-server")
                .serverPath(serverUrl()).clientType("streamable-http")
                .authHeaders(Map.of("X-Test-Auth", "secret-token"))
                .params(Map.of("_ojw_http_client", recording)).build();
    }

    private McpServerConfig plainConfig() {
        return McpServerConfig.builder().serverName("plain-server")
                .serverPath(serverUrl()).clientType("streamable-http").build();
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @SuppressWarnings("unchecked")
    private static HttpClient recordingHttpClient(List<HttpRequest> requests) throws Exception {
        HttpClient real = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
        HttpClient recording = mock(HttpClient.class, org.mockito.AdditionalAnswers.delegatesTo(real));
        doAnswer(invocation -> {
            Object rawRequest = invocation.getArgument(0);
            if (!(rawRequest instanceof HttpRequest request)) {
                throw new IllegalStateException("expected an HttpRequest argument");
            }
            requests.add(request);
            return real.send(request, (HttpResponse.BodyHandler<String>) invocation.getArgument(1));
        }).when(recording).send(any(HttpRequest.class), any());
        return recording;
    }

    private McpServerConfig stdioStubConfig(String... stubArgs) throws IOException {
        Path stubDir = Files.createTempDirectory("mcp-stdio-stub");
        stubDirs.add(stubDir);
        Files.writeString(stubDir.resolve("stdio_stub.py"), PYTHON_STUB, StandardCharsets.UTF_8);
        List<String> args = new ArrayList<>();
        args.add(stubDir.resolve("stdio_stub.py").toString());
        args.addAll(List.of(stubArgs));
        return McpServerConfig.builder().serverName("stdio-stub").clientType("stdio")
                .params(Map.of("command", "python3", "args", args, "cwd", stubDir.toString()))
                .build();
    }

    /**
     * Skips the enclosing test when the stdio stub interpreter is not on
     * the PATH, so environments without python3 report an explicit skip
     * instead of a connect failure.
     */
    private static void assumePython3Available() {
        Process process;
        try {
            // The version probe merges both child streams into one and
            // drains the merged stream below, so the child can never
            // block on an unread pipe (G.FIO.04).
            process = new ProcessBuilder("python3", "--version")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "python3 is not available for the stdio stub");
            return;
        }
        try {
            // The exit wait goes through the process handle with a bound;
            // the child has exited by then, so draining the merged output
            // cannot block. The drained version banner replaces the
            // exit-value probe (G.FIO.04).
            process.onExit().get(5L, TimeUnit.SECONDS);
            String versionBanner;
            try (InputStream output = process.getInputStream()) {
                versionBanner = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            }
            Assumptions.assumeTrue(versionBanner.startsWith("Python"),
                    "python3 is not runnable for the stdio stub");
        } catch (InterruptedException e) {
            // G.CON.10: no interrupt-flag restore; skip the test instead.
            Assumptions.assumeTrue(false, "python3 probe was interrupted");
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "python3 probe output could not be drained");
        } catch (ExecutionException | TimeoutException e) {
            Assumptions.assumeTrue(false, "python3 probe did not finish in time");
        } finally {
            process.destroyForcibly();
        }
    }

    private static McpServerConfig pathConfig(String serverId) {
        return McpServerConfig.builder().serverId(serverId).serverName(serverId)
                .serverPath("http://" + serverId).build();
    }

    private com.sun.net.httpserver.HttpServer startRespondingServer() throws IOException {
        return startServer(0L);
    }

    private com.sun.net.httpserver.HttpServer startDelayedToolsListServer() throws IOException {
        return startServer(2000L);
    }

    private com.sun.net.httpserver.HttpServer startServer(long toolsListDelayMillis) throws IOException {
        com.sun.net.httpserver.HttpServer httpServer =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/mcp", exchange -> {
            Map<String, Object> request = readJson(exchange);
            String method = String.valueOf(request.get("method"));
            if ("initialize".equals(method)) {
                writeJson(exchange, rpcResult(request.get("id"), Map.of("protocolVersion", "2024-11-05",
                        "capabilities", Map.of(), "serverInfo", Map.of("name", "mock", "version", "1.0"))));
                return;
            }
            if ("notifications/initialized".equals(method)) {
                writeEmpty(exchange);
                return;
            }
            if ("tools/list".equals(method)) {
                if (toolsListDelayMillis > 0L) {
                    try {
                        Thread.sleep(toolsListDelayMillis);
                    } catch (InterruptedException e) {
                        // G.CON.10: no interrupt-flag restore on this pooled
                        // server thread; drop the response so the client hits
                        // its own deadline instead of leaking the flag.
                        return;
                    }
                }
                writeJson(exchange, rpcResult(request.get("id"), Map.of("tools", List.of())));
                return;
            }
            writeJson(exchange, Map.of("jsonrpc", "2.0", "id", request.get("id"), "error",
                    Map.of("message", "unexpected method " + method)));
        });
        httpServer.start();
        return httpServer;
    }

    private static Map<String, Object> readJson(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        return MAPPER.readValue(exchange.getRequestBody(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    private static Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("result", result);
        return payload;
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, Map<String, Object> payload)
            throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static void writeEmpty(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
