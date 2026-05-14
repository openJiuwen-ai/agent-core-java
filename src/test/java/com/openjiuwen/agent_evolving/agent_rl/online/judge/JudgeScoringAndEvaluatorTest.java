/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeScoringAndEvaluatorTest {

    @Test
    void scoringBuildsPromptAndNormalizesOverall() {
        String prompt = JudgeScoring.buildJudgePrompt("inst", "resp", "feedback");
        assertTrue(prompt.contains("inst"));
        assertTrue(prompt.contains("resp"));
        assertTrue(prompt.contains("feedback"));
        assertEquals(0.6, JudgeScoring.normalizeOverallScore(8.0));
    }

    @Test
    void parseJudgeScoresFillsOverallFromDimensions() {
        Map<String, Object> parsed = JudgeScoring.parseJudgeScores(
                "{" +
                        "\"task_completion\":8,\"response_quality\":7,\"tool_usage\":9,\"coherence\":8" +
                        "}",
                true
        );
        assertEquals(8.0, ((Number) parsed.get("overall")).doubleValue());
    }

    @Test
    void evaluateJudgeScoresSanitizesPromptAndReturnsOverallRaw() {
        FakeJudgeClient client = new FakeJudgeClient(List.of(
                new GatewayHttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"overall\\\": 8, \\\"reason\\\": \\\"ok\\\"}\"}}]}")
        ));
        JudgeEvaluator evaluator = new JudgeEvaluator(client, millis -> { });
        JudgeEvaluatorConfig config = new JudgeEvaluatorConfig("http://judge.local", "judge-model");

        ScoreResponse result = evaluator.evaluateJudgeScores(
                config,
                "<tag>resp</tag>",
                "<tool_call>plan</tool_call>",
                "next",
                "s1",
                1
        );

        assertEquals(8.0, result.overallRaw());
        assertEquals(1, client.calls.size());
        String prompt = String.valueOf(((Map<?, ?>)((List<?>) client.calls.getFirst().payload().get("messages")).getFirst()).get("content"));
        assertTrue(prompt.contains("[tool_call block]"));
        assertTrue(prompt.contains("[tag]resp[/tag]"));
    }

    @Test
    void evaluateJudgeScoresRetriesOnRetryableStatus() {
        FakeJudgeClient client = new FakeJudgeClient(List.of(
                new GatewayHttpResponse(503, "{\"error\":\"busy\"}"),
                new GatewayHttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"overall\\\": 8}\"}}]}")
        ));
        JudgeEvaluator evaluator = new JudgeEvaluator(client, millis -> { });
        JudgeEvaluatorConfig config = new JudgeEvaluatorConfig("http://judge.local", "judge-model");
        config.setMaxRetries(1);
        config.setRetryBackoffSec(0.0);

        ScoreResponse result = evaluator.evaluateJudgeScores(config, "resp", "inst", "", "", 0);

        assertEquals(8.0, result.overallRaw());
        assertEquals(2, client.calls.size());
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
            throw new UnsupportedOperationException();
        }

        @Override
        public GatewayHttpResponse request(String method, String url, Map<String, Object> params, Map<String, String> headers, byte[] content) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(content, Map.class);
                calls.add(new Call(method, url, payload, headers));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return responses.get(index++);
        }
    }
}
