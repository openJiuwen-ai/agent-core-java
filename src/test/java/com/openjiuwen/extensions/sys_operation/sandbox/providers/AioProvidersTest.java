/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.sandbox.SandboxRegistry;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.sys_operation.sandbox.providers.aio} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/aio.py}.
 */
class AioProvidersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sandboxGatewayRegistersBuiltinAioProviders() {
        new SandboxGateway();

        assertSame(AioFsProvider.class, SandboxRegistry.getProviderCls("aio", "fs"));
        assertSame(AioShellProvider.class, SandboxRegistry.getProviderCls("aio", "shell"));
        assertSame(AioCodeProvider.class, SandboxRegistry.getProviderCls("aio", "code"));
    }

    @Test
    void shellProviderWrapsCommandAndSplitsStreams() throws Exception {
        AtomicReference<JsonNode> shellRequest = new AtomicReference<>();
        server = startServer(exchange -> {
            if ("/v1/shell/exec".equals(exchange.getRequestURI().getPath())) {
                shellRequest.set(readJson(exchange));
                writeJson(exchange, 200, Map.of(
                        "exit_code", 0,
                        "output", "stdout line\n__OJW_STDERR__:stderr line\n"));
                return;
            }
            writeJson(exchange, 404, Map.of("error", "not found"));
        });

        AioShellProvider provider = new AioShellProvider(endpoint(), gatewayConfig());
        ExecuteCmdResult result = provider.executeCmd(
                "echo hello",
                "/workspace/demo",
                9,
                Map.of("FOO", "bar baz"),
                null).join();

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("stdout line\n", result.getData().getStdout());
        assertEquals("stderr line\n", result.getData().getStderr());
        assertEquals(0, result.getData().getExitCode());

        String wrapped = shellRequest.get().path("command").asText();
        assertTrue(wrapped.contains("timeout 9s /bin/bash -lc"));
        assertTrue(wrapped.contains("/workspace/demo"));
        assertTrue(wrapped.contains("FOO"));
        assertTrue(wrapped.contains("bar baz"));
        assertTrue(wrapped.contains("echo hello"));
    }

    @Test
    void codeProviderIgnoresCwdAndFsSearchFallsBackFromGlob() throws Exception {
        AtomicReference<JsonNode> shellRequest = new AtomicReference<>();
        server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/v1/shell/exec".equals(path)) {
                shellRequest.set(readJson(exchange));
                writeJson(exchange, 200, Map.of("exit_code", 0, "output", "ok\n"));
                return;
            }
            if ("/v1/file/glob".equals(path)) {
                writeJson(exchange, 404, Map.of("error", "glob unsupported"));
                return;
            }
            if ("/v1/file/list".equals(path)) {
                writeJson(exchange, 200, Map.of(
                        "files", List.of(
                                Map.of(
                                        "name", "main.py",
                                        "path", "/tmp/main.py",
                                        "is_directory", false,
                                        "size", 3,
                                        "modified_time", "2026-06-09T00:00:00Z"),
                                Map.of(
                                        "name", "ignore.py",
                                        "path", "/tmp/ignore.py",
                                        "is_directory", false,
                                        "size", 5,
                                        "modified_time", "2026-06-09T00:00:01Z"),
                                Map.of(
                                        "name", "notes.txt",
                                        "path", "/tmp/notes.txt",
                                        "is_directory", false,
                                        "size", 5,
                                        "modified_time", "2026-06-09T00:00:02Z"))));
                return;
            }
            writeJson(exchange, 404, Map.of("error", "not found"));
        });

        AioCodeProvider codeProvider = new AioCodeProvider(endpoint(), gatewayConfig());
        ExecuteCodeResult codeResult = codeProvider.executeCode(
                "print('hello')",
                "python",
                7,
                Map.of(),
                "/should/be/ignored",
                null).join();

        assertEquals(StatusCode.SUCCESS.getCode(), codeResult.getCode());
        assertNotNull(codeResult.getData());
        String wrapped = shellRequest.get().path("command").asText();
        assertTrue(wrapped.contains("python -c"));
        assertFalse(wrapped.contains("/should/be/ignored"));

        AioFsProvider fsProvider = new AioFsProvider(endpoint(), gatewayConfig());
        SearchFilesResult searchResult = fsProvider.searchFiles("/tmp", "*.py", List.of("ignore*")).join();

        assertEquals(StatusCode.SUCCESS.getCode(), searchResult.getCode());
        assertEquals(1, searchResult.getData().getTotalMatches());
        assertEquals("main.py", searchResult.getData().getMatchingFiles().get(0).getName());
    }

    private SandboxEndpoint endpoint() {
        return new SandboxEndpoint("http://127.0.0.1:" + server.getAddress().getPort(), "unused");
    }

    private SandboxGatewayConfig gatewayConfig() {
        return SandboxGatewayConfig.builder()
                .launcherConfig(new PreDeployLauncherConfig("http://127.0.0.1:" + server.getAddress().getPort()))
                .timeoutSeconds(30)
                .build();
    }

    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> handler.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return JSON.readTree(inputStream);
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
