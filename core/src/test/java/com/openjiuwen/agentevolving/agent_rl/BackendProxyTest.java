/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's backend proxy checks in
 * {@code tests/system_tests/agent_evolving/agent_rl/proxy/test_backend_proxy_e2e.py}
 * and the indirect proxy usage contract in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/test_main_trainer.py}.
 */
class BackendProxyTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void startThenHealthReturns200() throws Exception {
        try (BackendProxy proxy = new BackendProxy()) {
            proxy.startSync();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.getUrl() + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("healthy"));
        }
    }

    @Test
    void updateBackendsViaPostProxyBackends() throws Exception {
        try (BackendProxy proxy = new BackendProxy()) {
            proxy.startSync();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.getUrl() + "/proxy/backends"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"servers\":[\"http://127.0.0.1:8000\"]}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(List.of("http://127.0.0.1:8000"), proxy.getBackendServers());
        }
    }

    @Test
    void modelsFallbackReturnsConfiguredModelWhenNoBackends() throws Exception {
        try (BackendProxy proxy = new BackendProxy(30_000, "custom-model")) {
            proxy.startSync();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.getUrl() + "/v1/models")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("custom-model"));
        }
    }

    @Test
    void updateBackendServersAcceptsSingleString() {
        try (BackendProxy proxy = new BackendProxy()) {
            proxy.updateBackendServers("127.0.0.1:9000");
            assertEquals(List.of("127.0.0.1:9000"), proxy.getBackendServers());
        }
    }

    @Test
    void proxyRouteWithoutBackendsReturns503() throws Exception {
        try (BackendProxy proxy = new BackendProxy()) {
            proxy.startSync();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.getUrl() + "/v1/chat/completions"))
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("No backend LLM servers available"));
        }
    }
}
