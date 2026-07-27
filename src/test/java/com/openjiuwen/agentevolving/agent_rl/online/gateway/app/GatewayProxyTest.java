/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java regression coverage for Python's gateway proxy entrypoints in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/proxy.py}.
 */
class GatewayProxyTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void buildCliConfigUsesDefaultsAndJudgeFallback() {
        GatewayConfig config = GatewayProxy.buildCliConfig(new String[]{"--port", "19090"});

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(19090, config.getPort());
        assertEquals("http://127.0.0.1:18000", config.getLlmUrl());
        assertEquals("http://127.0.0.1:18000", config.getJudgeUrl());
        assertEquals("records", config.getRecordDir());
        assertEquals("INFO", config.getLogLevel());
    }

    @Test
    void buildCliConfigHonorsExplicitArguments() {
        GatewayConfig config = GatewayProxy.buildCliConfig(new String[]{
                "--host", "0.0.0.0",
                "--port", "18081",
                "--llm-url", "http://llm.local:9000",
                "--judge-url", "http://judge.local:9001",
                "--model-id", "demo-model",
                "--judge-model", "judge-model",
                "--record-dir", "gateway-records",
                "--lora-repo-root", "lora-root",
                "--log-level", "DEBUG"
        });

        assertEquals("0.0.0.0", config.getHost());
        assertEquals(18081, config.getPort());
        assertEquals("http://llm.local:9000", config.getLlmUrl());
        assertEquals("http://judge.local:9001", config.getJudgeUrl());
        assertEquals("demo-model", config.getModelId());
        assertEquals("judge-model", config.getJudgeModel());
        assertEquals("gateway-records", config.getRecordDir());
        assertEquals("lora-root", config.getLoraRepoRoot());
        assertEquals("DEBUG", config.getLogLevel());
    }

    @Test
    void buildCliConfigRequiresPort() {
        assertThrows(IllegalArgumentException.class, () -> GatewayProxy.buildCliConfig(new String[]{"--host", "127.0.0.1"}));
    }

    @Test
    void hostedGatewayServesHealthAndCatchAllProxy() throws Exception {
        GatewayServer app = new GatewayServer(
                baseConfig(),
                new Forwarder(new NoOpUpstreamClient(), "demo-model"),
                new FixedUpstreamClient(new GatewayHttpResponse(
                        202,
                        "proxied",
                        Map.of("content-type", "text/plain", "connection", "close", "x-trace-id", "abc123"),
                        "text/plain"
                )),
                new FakeTrajectoryGateway(),
                null,
                null
        );

        try (GatewayProxy.HostedGatewayServer server = GatewayProxy.start("127.0.0.1", 0, app)) {
            String baseUrl = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"ok\""));

            HttpResponse<String> proxied = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models?limit=1"))
                            .header("Authorization", "Bearer gateway-secret")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(202, proxied.statusCode());
            assertEquals("abc123", proxied.headers().firstValue("x-trace-id").orElse(""));
            assertFalse(proxied.headers().firstValue("connection").isPresent());
            assertEquals("proxied", proxied.body());
        }
    }

    private static GatewayConfig baseConfig() {
        GatewayConfig config = new GatewayConfig();
        config.setGatewayApiKey("gateway-secret");
        config.setLlmApiKey("llm-secret");
        config.setModelId("demo-model");
        config.setLlmUrl("http://mock.llm/");
        config.setSingleUserDefault(true);
        return config;
    }

    private static final class FakeTrajectoryGateway implements GatewayServer.TrajectoryGateway {
        @Override
        public Map<String, Object> snapshotStats() {
            return Map.of(
                    "total_samples", 0,
                    "trajectory_store_total", 0,
                    "trajectory_store_pending", 0
            );
        }

        @Override
        public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
            return Map.of("accepted", 0, "rejected", 0);
        }
    }

    private static class NoOpUpstreamClient implements UpstreamGatewayClient {
        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            return new GatewayHttpResponse(200, "{}");
        }

        @Override
        public GatewayHttpResponse request(
                String method,
                String url,
                Map<String, Object> params,
                Map<String, String> headers,
                byte[] content
        ) {
            return new GatewayHttpResponse(200, "{}");
        }
    }

    private static final class FixedUpstreamClient extends NoOpUpstreamClient {
        private final GatewayHttpResponse response;

        private FixedUpstreamClient(GatewayHttpResponse response) {
            this.response = response;
        }

        @Override
        public GatewayHttpResponse request(
                String method,
                String url,
                Map<String, Object> params,
                Map<String, String> headers,
                byte[] content
        ) {
            return response;
        }
    }
}
