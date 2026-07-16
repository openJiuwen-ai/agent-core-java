/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

class JiuwenBoxClientTest {
    private MockWebServer server;
    private JiuwenBoxClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new JiuwenBoxClient(server.url("").toString(), 30);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("createSandbox returns sandbox ID from POST /api/v1/sandboxes")
    void testCreateSandbox() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"id\": \"sb-123\"}").setResponseCode(200));

        String sandboxId = client.createSandbox(Map.of());

        assertThat(sandboxId).isEqualTo("sb-123");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes");
    }

    @Test
    @DisplayName("deleteSandbox succeeds on 200 response")
    void testDeleteSandboxSuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertThatCode(() -> client.deleteSandbox("sb-123")).doesNotThrowAnyException();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/api/v1/sandboxes/sb-123");
    }

    @Test
    @DisplayName("deleteSandbox treats 404 as success")
    void testDeleteSandbox404IsSuccess() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        assertThatCode(() -> client.deleteSandbox("sb-123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deleteSandbox is no-op when sandboxId is null or empty")
    void testDeleteSandboxNoOp() {
        assertThatCode(() -> client.deleteSandbox(null)).doesNotThrowAnyException();
        assertThatCode(() -> client.deleteSandbox("")).doesNotThrowAnyException();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("exec returns stdout, stderr, and exitCode from response")
    void testExec() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"stdout\": \"hello\", \"stderr\": \"\", \"exit_code\": 0}")
                .setResponseCode(200));

        JiuwenBoxClient.ExecResponse resp =
            client.exec("sb-123", List.of("bash", "-lc", "echo hello"), ".", 30, null, null);

        assertThat(resp.getStdout()).isEqualTo("hello");
        assertThat(resp.getStderr()).isEqualTo("");
        assertThat(resp.getExitCode()).isEqualTo(0);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/exec");
    }

    @Test
    @DisplayName("uploadBytes sends multipart POST and succeeds on 200")
    void testUploadBytes() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        byte[] content = "file-data".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> client.uploadBytes("sb-123", "/root/a.txt", content)).doesNotThrowAnyException();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/upload");
        assertThat(request.getHeader("Content-Type")).contains("multipart/form-data");
    }

    @Test
    @DisplayName("downloadBytes returns byte content from GET response")
    void testDownloadBytes() throws Exception {
        byte[] expected = "binary-content".getBytes(StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(expected)));

        byte[] result = client.downloadBytes("sb-123", "/root/b.bin");

        assertThat(result).isEqualTo(expected);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/download");
    }

    @Test
    @DisplayName("listFiles parses items array from GET response")
    void testListFiles() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}")
                .setResponseCode(200));

        List<Map<String, Object>> items = client.listFiles("sb-123", "/root", false, null, true, false);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("path")).isEqualTo("/root/a.txt");
        assertThat(items.get(0).get("type")).isEqualTo("file");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/files");
    }

    @Test
    @DisplayName("searchFiles parses items array from GET response")
    void testSearchFiles() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}")
                .setResponseCode(200));

        List<Map<String, Object>> items = client.searchFiles("sb-123", "/root", "*.txt", null);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("path")).isEqualTo("/root/a.txt");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/search");
    }

    @Test
    @DisplayName("pathExists returns true when path is in listing")
    void testPathExistsFound() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [{\"path\": \"/root/a.txt\", \"type\": \"file\"}]}")
                .setResponseCode(200));

        boolean exists = client.pathExists("sb-123", "/root/a.txt");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("pathExists returns false when path is not in listing")
    void testPathExistsNotFound() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [{\"path\": \"/root/other.txt\", \"type\": \"file\"}]}")
                .setResponseCode(200));

        boolean exists = client.pathExists("sb-123", "/root/a.txt");
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("pathExists returns false on 404 without sandbox-not-found body")
    void testPathExists404ReturnsFalse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("resource not found"));

        boolean exists = client.pathExists("sb-123", "/root/missing");
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("setIdleTimeout sends PUT /api/v1/timeout")
    void testSetIdleTimeout() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertThatCode(() -> client.setIdleTimeout(600, 60)).doesNotThrowAnyException();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).startsWith("/api/v1/timeout");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("idle_timeout");
        assertThat(body).contains("idle_check_interval");
    }

    @Test
    @DisplayName("setIdleTimeout is no-op when both params are null")
    void testSetIdleTimeoutNoOp() {
        assertThatCode(() -> client.setIdleTimeout(null, null)).doesNotThrowAnyException();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("appendBytes encodes content as base64 and calls exec with decode command")
    void testAppendBytes() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"stdout\": \"\", \"stderr\": \"\", \"exit_code\": 0}")
                .setResponseCode(200));

        byte[] content = "append-me".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> client.appendBytes("sb-123", "/root/a.txt", content)).doesNotThrowAnyException();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/api/v1/sandboxes/sb-123/exec");
        String body = request.getBody().readUtf8();
        String expectedBase64 = Base64.getEncoder().encodeToString(content);
        assertThat(body).contains(expectedBase64);
        assertThat(body).contains("base64");
    }

    @Test
    @DisplayName("createSandbox throws RuntimeException on non-2xx response")
    void testCreateSandboxError() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error"));

        assertThatThrownBy(() -> client.createSandbox(Map.of())).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("exec throws SandboxNotFoundException on 404 with sandbox-not-found body")
    void testExecSandboxNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\": \"sandbox not found\"}"));

        assertThatThrownBy(() -> client.exec("sb-missing", List.of("bash", "-lc", "echo"), ".", 30, null, null))
                .isInstanceOf(SandboxNotFoundException.class);
    }
}
