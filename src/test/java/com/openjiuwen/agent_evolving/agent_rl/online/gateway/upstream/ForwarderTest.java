/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_forwarder_keeps_structured_tool_calls_unchanged} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/gateway/test_forwarder.py}.
 */
class ForwarderTest {

    @Test
    void forwarderKeepsStructuredToolCallsUnchanged() {
        String payload = """
                {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"call_1","type":"function","function":{"name":"read","arguments":"{\\\"file_path\\\":\\\"/tmp/a\\\"}"}}]},"finish_reason":"tool_calls"}]}
                """;
        FakeUpstreamClient upstreamClient = new FakeUpstreamClient(new GatewayHttpResponse(200, payload));
        Forwarder forwarder = new Forwarder(upstreamClient, "m1");

        Map<String, Object> result = forwarder.forward(Map.of("messages", List.of(Map.of("role", "user", "content", "hi"))), Map.of());

        assertEquals(expectedStructuredToolCallsPayload(), result);
        assertEquals("tool_calls", ((Map<?, ?>) ((List<?>) result.get("choices")).getFirst()).get("finish_reason"));
        assertEquals("m1", upstreamClient.calls.getFirst().jsonBody.get("model"));
        assertEquals(Boolean.TRUE, upstreamClient.calls.getFirst().jsonBody.get("logprobs"));
        assertEquals(1, upstreamClient.calls.getFirst().jsonBody.get("top_logprobs"));
    }

    @Test
    void cleanBodyDropsNonStandardFieldsAndDisablesStreaming() {
        FakeUpstreamClient upstreamClient = new FakeUpstreamClient(new GatewayHttpResponse(200, "{}"));
        Forwarder forwarder = new Forwarder(upstreamClient, "m1");

        Map<String, Object> cleaned = forwarder.cleanBody(new LinkedHashMap<>(Map.of(
                "messages", List.of(),
                "session_id", "sess-1",
                "stream", true,
                "stream_options", Map.of("include_usage", true)
        )));

        assertTrue(!cleaned.containsKey("session_id"));
        assertEquals(Boolean.FALSE, cleaned.get("stream"));
        assertTrue(!cleaned.containsKey("stream_options"));
    }

    @Test
    void forwarderWrapsUpstreamErrorsAsGateway502() {
        FakeUpstreamClient upstreamClient = new FakeUpstreamClient(new GatewayHttpResponse(503, "upstream error"));
        Forwarder forwarder = new Forwarder(upstreamClient, "m1");

        GatewayForwardingException error = assertThrows(
                GatewayForwardingException.class,
                () -> forwarder.forward(Map.of("messages", List.of()), Map.of())
        );

        assertEquals(502, error.getStatusCode());
        assertTrue(error.getMessage().contains("upstream error"));
    }

    static final class FakeUpstreamClient implements UpstreamGatewayClient {
        final GatewayHttpResponse response;
        final List<Call> calls = new ArrayList<>();

        FakeUpstreamClient(GatewayHttpResponse response) {
            this.response = response;
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            calls.add(new Call(jsonBody, headers));
            return response;
        }

        @Override
        public GatewayHttpResponse request(String method, String url, Map<String, Object> params,
                                           Map<String, String> headers, byte[] content) {
            calls.add(new Call(params != null ? params : Map.of(), headers != null ? headers : Map.of()));
            return response;
        }
    }

    record Call(Map<String, Object> jsonBody, Map<String, String> headers) {
    }

    private static Map<String, Object> expectedStructuredToolCallsPayload() {
        return Map.of("choices", List.of(Map.of(
                "message", Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of(
                                "id", "call_1",
                                "type", "function",
                                "function", Map.of(
                                        "name", "read",
                                        "arguments", "{\"file_path\":\"/tmp/a\"}"
                                )
                        ))
                ),
                "finish_reason", "tool_calls"
        )));
    }
}
