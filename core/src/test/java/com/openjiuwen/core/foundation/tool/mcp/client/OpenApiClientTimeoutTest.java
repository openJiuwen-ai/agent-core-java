/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Pins the {@link OpenApiClient} execution-timeout contract (baseline
 * semantics): positive timeouts bound the HTTP request, non-positive
 * values — including the {@link McpServerConfig#NO_TIMEOUT} sentinel —
 * mean no client-side execution bound. The bounded-discovery contract
 * (listTools fallback) is pinned by the client-layer cases in
 * {@link McpDiscoveryTimeoutTest}; OpenApiClient discovery is local and
 * needs no bound.
 *
 * @since 0.1.16
 */
@DisplayName("OpenApiClient call timeouts (client layer)")
class OpenApiClientTimeoutTest {
    private com.sun.net.httpserver.HttpServer server;

    @TempDir
    private Path specFile;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("positive callTool timeout interrupts a delayed OpenAPI endpoint")
    @Timeout(10)
    void positiveTimeoutInterruptsDelayedEndpoint() throws Exception {
        server = startDelayedServer(2000L);
        OpenApiClient client = new OpenApiClient(specConfig());

        assertThat(client.connect(0, McpServerConfig.NO_TIMEOUT)).isTrue();

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.callTool("slow_get", Map.of(), 0.5f))
                .as("callTool request timeout is enforced against a slow endpoint")
                .isInstanceOf(HttpTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis())
                .as("the request fails at the configured bound, not after the server delay")
                .isLessThan(1800L);
    }

    @Test
    @DisplayName("non-positive timeouts keep baseline semantics: normal calls complete")
    @Timeout(15)
    void nonPositiveTimeoutsCompleteUnbounded() throws Exception {
        server = startDelayedServer(500L);
        OpenApiClient client = new OpenApiClient(specConfig());

        assertThat(client.connect(0, McpServerConfig.NO_TIMEOUT)).isTrue();

        Object sentinelBody = client.callTool("slow_get", Map.of(), McpServerConfig.NO_TIMEOUT);
        assertThat(String.valueOf(sentinelBody)).as("NO_TIMEOUT sentinel stays callable").contains("ok");

        Object zeroBody = client.callTool("slow_get", Map.of(), 0f);
        assertThat(String.valueOf(zeroBody)).as("zero timeout stays callable").contains("ok");
    }

    @Test
    @Tag("system-test")
    @DisplayName("NO_TIMEOUT sentinel keeps baseline semantics: no client-side execution bound")
    @Timeout(45)
    void sentinelMeansNoClientBound_longDelayCompletes() throws Exception {
        server = startDelayedServer(31_000L);
        OpenApiClient client = new OpenApiClient(specConfig());

        assertThat(client.connect(0, McpServerConfig.NO_TIMEOUT)).isTrue();

        long start = System.nanoTime();
        Object body = client.callTool("slow_get", Map.of(), McpServerConfig.NO_TIMEOUT);
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        assertThat(String.valueOf(body)).as("the slow endpoint completes under the sentinel").contains("ok");
        assertThat(elapsedSeconds).as("the call waits out the server delay (31s), not a client cap")
                .isGreaterThanOrEqualTo(31L);
    }

    private McpServerConfig specConfig() throws IOException {
        return McpServerConfig.builder().serverName("openapi-timeout-server")
                .serverPath(writeSpec().toString()).clientType("openapi").build();
    }

    private Path writeSpec() throws IOException {
        String spec = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"timeout-spec\",\"version\":\"1.0\"},"
                + "\"servers\":[{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"}],"
                + "\"paths\":{\"/slow\":{\"get\":{\"operationId\":\"slow_get\",\"summary\":\"slow endpoint\"}}}}";
        return Files.writeString(specFile.resolve("openapi-timeout-spec.json"), spec, StandardCharsets.UTF_8);
    }

    private com.sun.net.httpserver.HttpServer startDelayedServer(long delayMillis) throws IOException {
        com.sun.net.httpserver.HttpServer httpServer =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(delayMillis);
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException e) {
                // G.CON.10: no interrupt-flag restore on this pooled server
                // thread; answer with 500 so the request fails fast instead
                // of leaving the client waiting for a response.
                exchange.sendResponseHeaders(500, -1);
            }
        });
        httpServer.start();
        return httpServer;
    }
}
