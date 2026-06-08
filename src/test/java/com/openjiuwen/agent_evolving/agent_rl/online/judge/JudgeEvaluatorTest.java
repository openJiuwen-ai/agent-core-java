/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeEvaluatorTest {

    @Test
    void buildJudgeMessagesSanitizesPromptFragments() {
        JudgeEvaluator evaluator = new JudgeEvaluator(new FakeUpstreamClient(List.of()), millis -> { });

        List<Map<String, String>> messages = evaluator.buildJudgeMessages(
                "<tool_call>hidden</tool_call>",
                "<x>tag</x>",
                ""
        );

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().get("content").contains("[tool_call block]"));
        assertTrue(messages.getFirst().get("content").contains("[tag]tag[/tag]"));
    }

    @Test
    void evaluateJudgeScoresParsesSingleVoteAndPreservesContext() {
        FakeUpstreamClient client = new FakeUpstreamClient(List.of(
                new GatewayHttpResponse(200, """
                        {"choices":[{"message":{"content":"{\\"overall\\": 8.0, \\"reason\\": \\"ok\\"}"}}]}
                        """)
        ));
        JudgeEvaluator evaluator = new JudgeEvaluator(client, millis -> { });
        JudgeEvaluatorConfig config = new JudgeEvaluatorConfig("http://judge.local", "judge-model");

        ScoreResponse response = evaluator.evaluateJudgeScores(config, "pong", "ping", "", "session-1", 2);

        assertEquals(0.6, response.score());
        assertEquals(8.0, response.overallRaw());
        assertEquals(List.of(8.0), response.votes());
        assertEquals("judge-model", response.model());
        assertEquals("session-1", response.sessionId());
        assertEquals(2, response.turnNum());
        assertEquals("POST", client.calls.getFirst().method());
        assertTrue(client.calls.getFirst().url().endsWith("/v1/chat/completions"));
    }

    @Test
    void flattenContentHandlesOpenAiStyleTextParts() {
        assertEquals("hello world", JudgeEvaluator.flattenContent(List.of(
                Map.of("type", "text", "text", "hello"),
                Map.of("type", "text", "text", "world")
        )));
    }

    private static final class FakeUpstreamClient implements UpstreamGatewayClient {
        private final List<GatewayHttpResponse> responses;
        private final List<Call> calls = new ArrayList<>();

        private FakeUpstreamClient(List<GatewayHttpResponse> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            return request("POST", "unused", Map.of(), headers, new byte[0]);
        }

        @Override
        public GatewayHttpResponse request(String method, String url, Map<String, Object> params, Map<String, String> headers,
                                           byte[] content) {
            calls.add(new Call(method, url, headers, new String(content, StandardCharsets.UTF_8)));
            return responses.removeFirst();
        }
    }

    private record Call(String method, String url, Map<String, String> headers, String body) {
    }
}
