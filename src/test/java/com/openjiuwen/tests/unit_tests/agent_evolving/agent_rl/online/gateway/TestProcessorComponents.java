/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online.gateway;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.JudgeDispatcher;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.JudgeScorer;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.PendingJudgeStore;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.SamplePayloads;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.SampleRecordingSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProcessorComponents.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/online/gateway/test_processor_components.py}.
 */
@DisplayName("ProcessorComponents Tests")
class TestProcessorComponents {

    @Test
    @DisplayName("build sample builds shared masks")
    void testBuildSampleBuildsSharedMasks() {
        Map<String, Object> sample = SamplePayloads.buildSample(
                "user-1",
                "s1",
                1,
                "judge_output",
                "string",
                "m1",
                List.of(Map.of("role", "user", "content", "hi")),
                List.of(Map.of("type", "function")),
                Map.of("role", "assistant", "content", "pong"),
                Map.of("total_tokens", 5),
                "stop",
                "prompt",
                List.of(1, 2, 3),
                "pong",
                List.of(4, 5),
                List.of(-0.1d, -0.2d),
                List.of(),
                Map.of("temperature", 0.2d),
                "sample-1",
                null,
                Map.of("rail_meta", Map.of("protocol_version", "rail-v1")));

        Map<String, Object> trajectory = castMap(sample.get("trajectory"));
        assertThat(trajectory.get("input_ids")).isEqualTo(List.of(1, 2, 3, 4, 5));
        assertThat(trajectory.get("attention_mask")).isEqualTo(List.of(1, 1, 1, 1, 1));
        assertThat(trajectory.get("response_mask")).isEqualTo(List.of(0, 0, 0, 1, 1));
        assertThat(castMap(sample.get("request"))).containsEntry("temperature", 0.2d);
        assertThat(castMap(sample.get("rail_meta"))).containsEntry("protocol_version", "rail-v1");
    }

    @Test
    @DisplayName("processor chat completion proxies without turn or sample work")
    void testProcessorChatCompletionProxiesWithoutTurnOrSampleWork() {
        FakeForwarder forwarder = new FakeForwarder();

        Map<String, Object> result = forwardChatCompletions(
                Map.of("x-request-id", "trace-9", "x-user-id", "user-9"),
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))),
                forwarder);

        assertThat(castMap(castMap(castList(result.get("choices")).getFirst()).get("message")))
                .containsEntry("content", "pong");
        assertThat(forwarder.forwardCalls).hasSize(1);
        assertThat(forwarder.forwardCalls.getFirst().get("body"))
                .isEqualTo(Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
    }

    @Test
    @DisplayName("judge dispatcher scores session done sample without followup feedback")
    void testJudgeDispatcherScoresSessionDoneSampleWithoutFollowupFeedback() {
        FakeRecorder recorder = new FakeRecorder();
        FakeJudgeScorer scorer = new FakeJudgeScorer(Map.of(
                "score", 0.25d,
                "votes", List.of(6.25d),
                "details", Map.of("overall", 6.25d)));
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", "sample-1");
        sample.put("user_id", "user-1");
        sample.put("session_id", "s1");
        sample.put("turn_num", 1);
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("trajectory", Map.of("response_text", "pong"));
        JudgeDispatcher dispatcher = new JudgeDispatcher(new FakePendingStore(List.of(sample)), recorder, scorer);

        int count = dispatcher.onSessionDone("s1");

        assertThat(count).isEqualTo(1);
        assertThat(scorer.calls).hasSize(1);
        assertThat(scorer.calls.getFirst()).containsEntry("instruction_text", "hello");
        assertThat(scorer.calls.getFirst()).containsEntry("followup_user_feedback", "");
        assertThat(castMap(recorder.samples.getFirst().get("judge"))).containsEntry("score", 0.25d);
        assertThat(castMap(recorder.samples.getFirst().get("judge_feedback"))).containsEntry("tag", "session_done");
    }

    private static Map<String, Object> forwardChatCompletions(Map<String, String> headers,
                                                              Map<String, Object> body,
                                                              FakeForwarder forwarder) {
        return forwarder.forward(body, headers);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    static final class FakeForwarder {
        private final List<Map<String, Object>> forwardCalls = new ArrayList<>();

        public Map<String, Object> forward(Map<String, Object> body, Map<String, String> headers) {
            forwardCalls.add(Map.of("body", body, "headers", headers));
            return Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", "pong"))));
        }
    }

    static final class FakeJudgeScorer implements JudgeScorer {
        private final List<Map<String, Object>> calls = new ArrayList<>();
        private final Map<String, Object> scoreResult;
        private boolean closed;

        FakeJudgeScorer(Map<String, Object> scoreResult) {
            this.scoreResult = scoreResult != null ? scoreResult : Map.of("score", 0.75d, "votes", List.of("ok"), "details", Map.of());
        }

        @Override
        public Map<String, Object> score(String responseText,
                                         String instructionText,
                                         String followupUserFeedback,
                                         String sessionId,
                                         int turnNum) {
            calls.add(Map.of(
                    "response_text", responseText,
                    "instruction_text", instructionText,
                    "followup_user_feedback", followupUserFeedback,
                    "session_id", sessionId,
                    "turn_num", turnNum));
            return new LinkedHashMap<>(scoreResult);
        }

        public void close() {
            closed = true;
        }
    }

    static final class FakePendingStore extends PendingJudgeStore {
        private final List<Map<String, Object>> samples;

        FakePendingStore(List<Map<String, Object>> samples) {
            this.samples = new ArrayList<>(samples);
        }

        @Override
        public List<Map<String, Object>> popAll(String sessionId) {
            List<Map<String, Object>> out = new ArrayList<>(samples);
            samples.clear();
            return out;
        }
    }

    static final class FakeRecorder implements SampleRecordingSink {
        private final List<Map<String, Object>> samples = new ArrayList<>();

        @Override
        public void recordSample(Map<String, Object> sample) {
            samples.add(new LinkedHashMap<>(sample));
        }
    }
}
