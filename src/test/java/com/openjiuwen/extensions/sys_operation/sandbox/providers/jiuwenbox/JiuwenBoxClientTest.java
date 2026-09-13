/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import okhttp3.HttpUrl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class JiuwenBoxClientTest {
    private TestHttpServer server;
    private JiuwenBoxClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        client = new JiuwenBoxClient(server.baseUrl(), 30);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("createSandbox returns sandbox ID from POST /api/v1/sandboxes")
    void testCreateSandbox() {
        server.enqueue(200, "{\"id\": \"sb-123\"}");

        String sandboxId = client.createSandbox(Map.of());

        assertThat(sandboxId).isEqualTo("sb-123");
        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).startsWith("/api/v1/sandboxes");
    }

    @Test
    @DisplayName("deleteSandbox succeeds on 200 response")
    void testDeleteSandboxSuccess() {
        server.enqueue(200);

        assertThatCode(() -> client.deleteSandbox("sb-123")).doesNotThrowAnyException();

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("DELETE");
        assertThat(request.path()).isEqualTo("/api/v1/sandboxes/sb-123");
    }

    @Test
    @DisplayName("deleteSandbox treats 404 as success")
    void testDeleteSandbox404IsSuccess() {
        server.enqueue(404, "not found");

        assertThatCode(() -> client.deleteSandbox("sb-123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deleteSandbox is no-op when sandboxId is null or empty")
    void testDeleteSandboxNoOp() {
        assertThatCode(() -> client.deleteSandbox(null)).doesNotThrowAnyException();
        assertThatCode(() -> client.deleteSandbox("")).doesNotThrowAnyException();
        assertThat(server.requestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("exec returns stdout, stderr, and exitCode from response")
    void testExec() {
        server.enqueue(200, "{\"stdout\": \"hello\", \"stderr\": \"\", \"exit_code\": 0}");

        JiuwenBoxClient.ExecResponse response =
                client.exec("sb-123", List.of("bash", "-lc", "echo hello"), ".", 30, null, null);

        assertThat(response.getStdout()).isEqualTo("hello");
        assertThat(response.getStderr()).isEqualTo("");
        assertThat(response.getExitCode()).isEqualTo(0);

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/exec");
    }

    @Test
    @DisplayName("uploadBytes sends multipart POST and succeeds on 200")
    void testUploadBytes() {
        server.enqueue(200);

        byte[] content = "file-data".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> client.uploadBytes("sb-123", "/root/a.txt", content)).doesNotThrowAnyException();

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/upload");
        assertThat(request.contentType()).contains("multipart/form-data");
    }

    @Test
    @DisplayName("downloadBytes returns byte content from GET response")
    void testDownloadBytes() {
        byte[] expected = "binary-content".getBytes(StandardCharsets.UTF_8);
        server.enqueue(200, expected);

        byte[] result = client.downloadBytes("sb-123", "/root/b.bin");

        assertThat(result).isEqualTo(expected);
        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/download");
    }

    @Test
    @DisplayName("listFiles parses items array from GET response")
    void testListFiles() {
        server.enqueue(200, "{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}");

        List<Map<String, Object>> items = client.listFiles("sb-123", "/root", false, null, true, false);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("path")).isEqualTo("/root/a.txt");
        assertThat(items.get(0).get("type")).isEqualTo("file");

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/files");
    }

    @Test
    @DisplayName("searchFiles parses items array from GET response")
    void testSearchFiles() {
        server.enqueue(200, "{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}");

        List<Map<String, Object>> items = client.searchFiles("sb-123", "/root", "*.txt", null);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("path")).isEqualTo("/root/a.txt");

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/search");
    }

    @Test
    @DisplayName("pathExists returns true when path is in listing")
    void testPathExistsFound() {
        server.enqueue(200, "{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}");

        boolean isExisting = client.pathExists("sb-123", "/root/a.txt");

        assertThat(isExisting).isTrue();
    }

    @Test
    @DisplayName("pathExists returns false when path is not in listing")
    void testPathExistsNotFound() {
        server.enqueue(200, "{\"items\": [{\"path\": \"/root/other.txt\", \"type\": \"file\"}]}");

        boolean isExisting = client.pathExists("sb-123", "/root/a.txt");

        assertThat(isExisting).isFalse();
    }

    @Test
    @DisplayName("pathExists returns false on 404 without sandbox-not-found body")
    void testPathExists404ReturnsFalse() {
        server.enqueue(404, "resource not found");

        boolean isExisting = client.pathExists("sb-123", "/root/missing");

        assertThat(isExisting).isFalse();
    }

    @Test
    @DisplayName("setIdleTimeout sends PUT /api/v1/timeout")
    void testSetIdleTimeout() {
        server.enqueue(200);

        assertThatCode(() -> client.setIdleTimeout(600, 60)).doesNotThrowAnyException();

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.path()).startsWith("/api/v1/timeout");
        assertThat(request.body()).contains("idle_timeout");
        assertThat(request.body()).contains("idle_check_interval");
    }

    @Test
    @DisplayName("setIdleTimeout is no-op when both params are null")
    void testSetIdleTimeoutNoOp() {
        assertThatCode(() -> client.setIdleTimeout(null, null)).doesNotThrowAnyException();
        assertThat(server.requestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("appendBytes encodes content as base64 and calls exec with decode command")
    void testAppendBytes() {
        server.enqueue(200, "{\"stdout\": \"\", \"stderr\": \"\", \"exit_code\": 0}");

        byte[] content = "append-me".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> client.appendBytes("sb-123", "/root/a.txt", content)).doesNotThrowAnyException();

        CapturedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).startsWith("/api/v1/sandboxes/sb-123/exec");
        String expectedBase64 = Base64.getEncoder().encodeToString(content);
        assertThat(request.body()).contains(expectedBase64);
        assertThat(request.body()).contains("base64");
    }

    @Test
    @DisplayName("createSandbox throws RuntimeException on non-2xx response")
    void testCreateSandboxError() {
        server.enqueue(500, "internal error");

        assertThatThrownBy(() -> client.createSandbox(Map.of())).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("exec throws SandboxNotFoundException on 404 with sandbox-not-found body")
    void testExecSandboxNotFound() {
        server.enqueue(404, "{\"error\": \"sandbox not found\"}");

        assertThatThrownBy(() -> client.exec("sb-missing", List.of("bash", "-lc", "echo"), ".", 30, null, null))
                .isInstanceOf(SandboxNotFoundException.class);
    }

    private record CapturedRequest(String method, String path, String contentType, String body) {
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer httpServer;
        private final AtomicReference<StubResponse> nextResponse = new AtomicReference<>();
        private final AtomicReference<CapturedRequest> lastRequest = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();

        private TestHttpServer() throws IOException {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            httpServer = HttpServer.create(address, 0);
            httpServer.createContext("/", this::handle);
            httpServer.start();
        }

        @Override
        public void close() {
            httpServer.stop(0);
        }

        private String baseUrl() {
            InetSocketAddress address = httpServer.getAddress();
            return new HttpUrl.Builder().scheme("http").host(address.getAddress().getHostAddress())
                    .port(address.getPort()).build().toString();
        }

        private void enqueue(int statusCode) {
            enqueue(statusCode, new byte[0]);
        }

        private void enqueue(int statusCode, String body) {
            enqueue(statusCode, body.getBytes(StandardCharsets.UTF_8));
        }

        private void enqueue(int statusCode, byte[] body) {
            StubResponse response = new StubResponse(statusCode, body);
            if (!nextResponse.compareAndSet(null, response)) {
                throw new IllegalStateException("A response is already queued for this test server");
            }
        }

        private int requestCount() {
            return requestCount.get();
        }

        private CapturedRequest takeRequest() {
            CapturedRequest request = lastRequest.getAndSet(null);
            if (request == null) {
                throw new AssertionError("Expected the test server to receive a request");
            }
            return request;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                requestCount.incrementAndGet();
                lastRequest.set(captureRequest(exchange));
                StubResponse response = nextResponse.getAndSet(null);
                if (response == null) {
                    sendResponse(exchange, new StubResponse(500,
                            "No response queued by the test".getBytes(StandardCharsets.UTF_8)));
                    return;
                }
                sendResponse(exchange, response);
            }
        }

        private static CapturedRequest captureRequest(HttpExchange exchange) throws IOException {
            String body;
            try (InputStream inputStream = exchange.getRequestBody()) {
                body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String contentType = Objects.toString(exchange.getRequestHeaders().getFirst("Content-Type"), "");
            return new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(), contentType,
                    body);
        }

        private static void sendResponse(HttpExchange exchange, StubResponse response) throws IOException {
            byte[] body = response.body();
            exchange.sendResponseHeaders(response.statusCode(), body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static final class StubResponse {
        private final int statusCode;
        private final byte[] body;

        private StubResponse(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = Arrays.copyOf(body, body.length);
        }

        private int statusCode() {
            return statusCode;
        }

        private byte[] body() {
            return Arrays.copyOf(body, body.length);
        }
    }
}
