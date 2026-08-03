/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeScorerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void parseScoresHandlesMultipleCodeBlocksAndAliases() {
        String content = """
                preface
                ```text
                ignored
                ```
                ```json
                {"task_completion_score": 8, "response_quality": 7, "tool_usage_score": 9, "coherence": 6}
                ```
                """;

        Map<String, Object> scores = JudgeScorer.parseScores(content);

        assertEquals(8, ((Number) scores.get("task_completion_score")).intValue());
        assertEquals(7.5, ((Number) scores.get("overall")).doubleValue());
    }

    @Test
    void scoreRetriesLengthSanitizesPromptAndDropsInternalFields() {
        FakeJudgeClient client = new FakeJudgeClient(List.of(
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"<tag>bad</tag>\"}}]}"),
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"overall\\\": 8, \\\"reason\\\": \\\"ok\\\"}\"}}]}")
        ));
        JudgeScorer scorer = new JudgeScorer(
                "http://judge.local/",
                "judge-model",
                "EMPTY",
                60.0,
                1,
                2,
                0.0,
                client
        );

        Map<String, Object> result = scorer.score("<tag>resp</tag>", "<tool_call>plan</tool_call>", "next", "", 0).join();

        assertEquals(8.0, ((Number) result.get("overall_raw")).doubleValue());
        assertFalse(result.containsKey("model"));
        assertFalse(result.containsKey("session_id"));
        assertFalse(result.containsKey("turn_num"));
        assertEquals(2, client.calls.size());
        assertEquals("http://judge.local/v1/chat/completions", client.calls.get(0).url());
        String prompt = String.valueOf(
                ((Map<?, ?>) ((List<?>) client.calls.get(0).payload().get("messages")).get(0)).get("content")
        );
        assertTrue(prompt.contains("[tool_call block]"));
        assertTrue(prompt.contains("[tag]resp[/tag]"));
    }

    record Call(String method, String url, Map<String, Object> payload, Map<String, String> headers) {
    }

    static final class FakeJudgeClient implements UpstreamGatewayClient {
        private final List<GatewayHttpResponse> responses;
        private int index;
        final List<Call> calls = new ArrayList<>();

        FakeJudgeClient(List<GatewayHttpResponse> responses) {
            this.responses = responses;
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            calls.add(new Call("POST", "chat.completions", jsonBody, headers));
            return responses.get(index++);
        }

        @Override
        public GatewayHttpResponse request(String method,
                                           String url,
                                           Map<String, Object> params,
                                           Map<String, String> headers,
                                           byte[] content) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = OBJECT_MAPPER.readValue(content, Map.class);
                calls.add(new Call(method, url, payload, headers));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to capture judge request", exception);
            }
            return responses.get(index++);
        }
    }
}
