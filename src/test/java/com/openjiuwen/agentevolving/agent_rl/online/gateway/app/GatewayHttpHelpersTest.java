package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayHttpHelpersTest {

    @Test
    void ensureGatewayAuthRejectsMissingAndInvalidBearerTokens() {
        assertDoesNotThrow(() -> GatewayHttpHelpers.ensureGatewayAuth("", null));

        GatewayHttpException missing = assertThrows(
                GatewayHttpException.class,
                () -> GatewayHttpHelpers.ensureGatewayAuth("secret", null)
        );
        assertEquals(401, missing.getStatusCode());
        assertEquals("missing bearer token", missing.getDetail());

        GatewayHttpException invalid = assertThrows(
                GatewayHttpException.class,
                () -> GatewayHttpHelpers.ensureGatewayAuth("secret", "Bearer wrong")
        );
        assertEquals(403, invalid.getStatusCode());
        assertEquals("invalid bearer token", invalid.getDetail());
    }

    @Test
    void buildUpstreamHeadersFiltersForwardingHeadersAndInjectsAuthorization() {
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Host", "gateway.local");
        requestHeaders.put("content-length", "99");
        requestHeaders.put("Connection", "keep-alive");
        requestHeaders.put("X-Forwarded-For", "127.0.0.1");
        requestHeaders.put("User-Agent", "gateway-test");
        requestHeaders.put("x-custom", "value");

        Map<String, String> upstreamHeaders = GatewayHttpHelpers.buildUpstreamHeaders(requestHeaders, "llm-secret");

        assertFalse(upstreamHeaders.containsKey("Host"));
        assertFalse(upstreamHeaders.containsKey("content-length"));
        assertFalse(upstreamHeaders.containsKey("Connection"));
        assertFalse(upstreamHeaders.containsKey("X-Forwarded-For"));
        assertEquals("gateway-test", upstreamHeaders.get("User-Agent"));
        assertEquals("value", upstreamHeaders.get("x-custom"));
        assertEquals("Bearer llm-secret", upstreamHeaders.get("Authorization"));
    }

    @Test
    void streamChatResponsePreservesRuntimeTokenFields() {
        Map<String, Object> logprobItem1 = new LinkedHashMap<>();
        logprobItem1.put("logprob", -0.1d);
        Map<String, Object> logprobItem2 = new LinkedHashMap<>();
        logprobItem2.put("logprob", -0.2d);
        Map<String, Object> logprobs = new LinkedHashMap<>();
        logprobs.put("content", List.of(logprobItem1, logprobItem2));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "pong");

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        choice.put("token_ids", List.of(4, 5));
        choice.put("logprobs", logprobs);
        choice.put("message", message);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 3);
        usage.put("completion_tokens", 2);
        usage.put("total_tokens", 5);

        Map<String, Object> responseJson = new LinkedHashMap<>();
        responseJson.put("id", "chatcmpl-test");
        responseJson.put("object", "chat.completion");
        responseJson.put("created", 123);
        responseJson.put("model", "m1");
        responseJson.put("prompt_token_ids", List.of(1, 2, 3));
        responseJson.put("usage", usage);
        responseJson.put("choices", List.of(choice));

        List<String> chunks = GatewayHttpHelpers.streamChatResponse(responseJson, "m1");

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).contains("\"prompt_token_ids\": [1, 2, 3]"));
        assertTrue(chunks.get(0).contains("\"token_ids\": [4, 5]"));
        assertTrue(chunks.get(0).contains("\"logprobs\": {\"content\": [{\"logprob\": -0.1}, {\"logprob\": -0.2}]}"));
        assertTrue(chunks.get(1).contains("\"usage\": {\"prompt_tokens\": 3, \"completion_tokens\": 2, \"total_tokens\": 5}"));
        assertEquals("data: [DONE]\n\n", chunks.get(2));
    }
}
