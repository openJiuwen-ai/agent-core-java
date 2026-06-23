/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's upstream HTTP client retry tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/gateway/test_upstream_client.py}.
 */
class HttpUpstreamGatewayClientTest {

    @Test
    void postChatCompletionsRetriesOnRetryableStatus() {
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
                millis -> {
                },
                Duration.ofSeconds(3)
        );

        GatewayHttpResponse response = client.postChatCompletions(Map.of("messages", List.of()), Map.of());

        assertEquals(200, response.statusCode());
        assertEquals(2, requests.size());
        assertEquals("/v1/chat/completions", requests.getFirst().uri().getPath());
    }

    @Test
    void requestRetriesOnIoExceptionThenSucceeds() {
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
                millis -> {
                },
                Duration.ofSeconds(3)
        );

        GatewayHttpResponse response = client.request("GET", "http://mock.local/v1/models", Map.of(), Map.of(), new byte[0]);

        assertEquals(200, response.statusCode());
        assertEquals(2, requests.size());
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

        abstract GatewayHttpResponse nextResponse(int callIndex, HttpRequest request) throws IOException, InterruptedException;
    }
}
