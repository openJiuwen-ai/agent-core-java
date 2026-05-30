/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for delayed-judge dispatch.
 * <p>
 * Mirrors Python's {@code JudgeDispatcher} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.judge_dispatcher}.
 */
class JudgeDispatcherTest {

    @Test
    void onPrevFeedbackReturnsZeroForBlankFeedbackAndMissingPendingSample() {
        FakePendingStore store = new FakePendingStore(List.of());
        FakeRecorder recorder = new FakeRecorder();
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, recorder, null);

        assertEquals(0, dispatcher.onPrevFeedback("s1", Map.of("text", "   ")));
        assertEquals(0, dispatcher.onPrevFeedback("s1", Map.of("text", "next user turn")));
        assertEquals(List.of(), recorder.samples);
    }

    @Test
    void onSessionDoneScoresSampleWithoutFollowupFeedback() {
        FakeRecorder recorder = new FakeRecorder();
        FakeScorer scorer = new FakeScorer(Map.of("score", 0.25, "votes", List.of(6.25), "details", Map.of("overall", 6.25)));
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", "sample-1");
        sample.put("user_id", "user-1");
        sample.put("session_id", "s1");
        sample.put("turn_num", 1);
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("trajectory", Map.of("response_text", "pong"));
        JudgeDispatcher dispatcher = new JudgeDispatcher(new FakePendingStore(List.of(sample)), recorder, scorer);

        int count = dispatcher.onSessionDone("s1");

        assertEquals(1, count);
        assertEquals(1, scorer.calls.size());
        assertEquals("hello", scorer.calls.getFirst().get("instruction_text"));
        assertEquals("", scorer.calls.getFirst().get("followup_user_feedback"));
        assertEquals(0.25, recorder.samples.getFirst().get("judge_score"));
        assertEquals("session_done", recorder.samples.getFirst().get("tag"));
    }

    @Test
    void finalizeSampleUsesPythonTruthyFallbacks() {
        FakeScorer scorer = new FakeScorer(Map.of("score", 0.5, "votes", List.of("ok"), "details", Map.of()));
        JudgeDispatcher dispatcher = new JudgeDispatcher(new FakePendingStore(List.of()), sample -> { }, scorer);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", null);
        sample.put("session_id", "s1");
        sample.put("turn_num", 0);
        sample.put("step_index", 7);
        sample.put("response_text", "top-level-response");
        sample.put("trajectory", Map.of("response_text", ""));
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "   "))));

        Map<String, Object> finalized = dispatcher.finalizeSample(sample, "feedback", "prev_feedback");

        assertEquals(7, scorer.calls.getFirst().get("turn_num"));
        assertEquals("top-level-response", scorer.calls.getFirst().get("response_text"));
        assertEquals("   ", scorer.calls.getFirst().get("instruction_text"));
        assertNull(finalized.get("sample_id"));
    }

    static final class FakePendingStore extends PendingJudgeStore {
        private final List<Map<String, Object>> samples;

        FakePendingStore(List<Map<String, Object>> samples) {
            this.samples = new ArrayList<>(samples);
        }

        @Override
        public Map<String, Object> popEarliest(String sessionId) {
            if (samples.isEmpty()) {
                return null;
            }
            return samples.removeFirst();
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

        @SuppressWarnings("unchecked")
        @Override
        public void recordSample(Map<String, Object> sample) {
            Map<String, Object> recorded = new LinkedHashMap<>();
            recorded.put("tag", ((Map<String, Object>) sample.get("judge_feedback")).get("tag"));
            recorded.put("judge_score", ((Map<String, Object>) sample.get("judge")).get("score"));
            samples.add(recorded);
        }
    }

    static final class FakeScorer implements JudgeScorer {
        private final Map<String, Object> result;
        private final List<Map<String, Object>> calls = new ArrayList<>();

        FakeScorer(Map<String, Object> result) {
            this.result = result;
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
                    "turn_num", turnNum
            ));
            return result;
        }
    }
}
