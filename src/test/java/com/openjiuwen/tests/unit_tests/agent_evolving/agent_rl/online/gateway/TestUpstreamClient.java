/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online.gateway;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UpstreamClient.
 * <p>
 * Mirrors Python's {@code test_upstream_client.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/gateway/}.
 */
@DisplayName("UpstreamClient Tests")
class TestUpstreamClient {

    @Test
    @DisplayName("post chat completions retries on retryable status")
    void testPostChatCompletionsRetriesOnRetryableStatus() {
        List<HttpRequest> requests = new ArrayList<>();
        CountingTransport transport = new CountingTransport(requests) {
            @Override
            GatewayHttpResponse nextResponse(int callIndex, HttpRequest request) {
                return callIndex == 0
                        ? new GatewayHttpResponse(503, "{\"error\":\"busy\"}")
                        : new GatewayHttpResponse(200, "{\"ok\":true}");
            }
        };

        HttpUpstreamGatewayClient client = new HttpUpstreamGatewayClient(
                transport,
                "http://mock.local",
                new RetryPolicy(2, 0.0, 0.0),
                Duration.ofSeconds(3)
        );

        GatewayHttpResponse response = client.postChatCompletions(Map.of("messages", List.of()), Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().uri().getPath()).isEqualTo("/v1/chat/completions");
    }

    @Test
    @DisplayName("request retries on connect error then succeeds")
    void testRequestRetriesOnConnectErrorThenSucceeds() {
        List<HttpRequest> requests = new ArrayList<>();
        CountingTransport transport = new CountingTransport(requests) {
            @Override
            GatewayHttpResponse nextResponse(int callIndex, HttpRequest request) throws IOException {
                if (callIndex == 0) {
                    throw new IOException("dial failed");
                }
                return new GatewayHttpResponse(200, "{\"ok\":true}");
            }
        };

        HttpUpstreamGatewayClient client = new HttpUpstreamGatewayClient(
                transport,
                "http://mock.local",
                new RetryPolicy(3, 0.0, 0.0),
                Duration.ofSeconds(3)
        );

        GatewayHttpResponse response = client.request("GET", "http://mock.local/v1/models", Map.of(), Map.of(),
                new byte[0]);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(requests).hasSize(2);
    }

    abstract static class CountingTransport implements GatewayHttpTransport {
        private final List<HttpRequest> requests;
        private int callIndex;

        CountingTransport(List<HttpRequest> requests) {
            this.requests = requests;
        }

        @Override
        public GatewayHttpResponse send(HttpRequest request) throws IOException, InterruptedException {
            requests.add(request);
            return nextResponse(callIndex++, request);
        }

        abstract GatewayHttpResponse nextResponse(int callIndex, HttpRequest request)
                throws IOException, InterruptedException;
    }
}
