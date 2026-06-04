/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RlProxyTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void getProxyConfigFromEnvReadsSystemProperties() {
        System.setProperty("AGENT_RL_PROXY_TIMEOUT_SECONDS", "12.5");
        System.setProperty("AGENT_RL_PROXY_MODEL_NAME", "demo-model");
        System.setProperty("AGENT_RL_PROXY_BACKENDS", "127.0.0.1:8000,127.0.0.1:8001");
        try {
            RlProxy.BackendProxyConfig config = RlProxy.getProxyConfigFromEnv();

            assertEquals(12.5, config.llmTimeoutSeconds());
            assertEquals("demo-model", config.modelName());
            assertEquals(List.of("127.0.0.1:8000", "127.0.0.1:8001"), config.backendServers());
        } finally {
            System.clearProperty("AGENT_RL_PROXY_TIMEOUT_SECONDS");
            System.clearProperty("AGENT_RL_PROXY_MODEL_NAME");
            System.clearProperty("AGENT_RL_PROXY_BACKENDS");
        }
    }

    @Test
    void backendProxyServesHealthAndFallbackModels() throws Exception {
        RlProxy.BackendProxy proxy = RlProxy.createAgentProxy(null);
        proxy.start();
        try {
            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create(proxy.url() + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> models = client.send(
                HttpRequest.newBuilder(URI.create(proxy.url() + "/v1/models")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"healthy\""));
            assertEquals(200, models.statusCode());
            assertTrue(models.body().contains("\"id\":\"agentrl\""));
        } finally {
            proxy.close();
        }
    }

    @Test
    void backendProxyForwardsOpenAiRequestWithModelAndNonStreaming() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        backend.start();

        RlProxy.BackendProxy proxy = new RlProxy.BackendProxy(new RlProxy.BackendProxyConfig(
            30,
            "agentrl",
            List.of("127.0.0.1:" + backend.getAddress().getPort())
        ));
        proxy.start();
        try {
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(proxy.url() + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"messages\":[]}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode(), response.body());
            assertEquals("{\"ok\":true}", response.body());
            assertTrue(capturedBody.get().contains("\"model\":\"agentrl\""));
            assertTrue(capturedBody.get().contains("\"stream\":false"));
        } finally {
            proxy.close();
            backend.stop(0);
        }
    }

    @Test
    void backendUpdateEndpointReplacesServers() throws Exception {
        RlProxy.BackendProxy proxy = RlProxy.createAgentProxy(null);
        proxy.start();
        try {
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(proxy.url() + "/proxy/backends"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"servers\":[\"127.0.0.1:9000\"]}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(List.of("127.0.0.1:9000"), proxy.backendServers());
            assertTrue(response.body().contains("\"status\":\"ok\""));
        } finally {
            proxy.close();
        }
    }
}
