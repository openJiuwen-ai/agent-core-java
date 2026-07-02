/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JudgeDispatcherTest {

    @Test
    void onPrevFeedbackReturnsZeroForBlankFeedbackAndMissingPendingSample() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        FakeRecorder recorder = new FakeRecorder();
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, recorder, null);

        assertEquals(0, dispatcher.onPrevFeedback("s1", Map.of("text", "   ")));
        assertEquals(0, dispatcher.onPrevFeedback("s1", Map.of("text", "next user turn")));
        assertEquals(List.of(), recorder.samples);
    }

    @Test
    void onSessionDoneScoresSampleWithoutFollowupFeedback() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", "sample-1");
        sample.put("user_id", "user-1");
        sample.put("session_id", "s1");
        sample.put("turn_num", 1);
        sample.put("trajectory_id", "traj-1");
        sample.put("step_index", 0);
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("trajectory", Map.of("response_text", "pong"));
        store.put(sample);

        FakeRecorder recorder = new FakeRecorder();
        FakeScorer scorer = new FakeScorer(Map.of("score", 0.25, "votes", List.of(6.25), "details", Map.of("overall", 6.25)));
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, recorder, scorer);

        int count = dispatcher.onSessionDone("s1");

        assertEquals(1, count);
        assertEquals(1, scorer.calls.size());
        assertEquals("hello", scorer.calls.get(0).get("instruction_text"));
        assertEquals("", scorer.calls.get(0).get("followup_user_feedback"));
        assertEquals(0.25, recorder.samples.get(0).get("judge_score"));
        assertEquals("session_done", recorder.samples.get(0).get("tag"));
    }

    @Test
    void finalizeSampleAddsGeneratedSampleIdWhenMissing() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, sample -> { }, null);

        Map<String, Object> finalized = dispatcher.finalizeSample(
                new LinkedHashMap<>(Map.of(
                        "session_id", "s1",
                        "request", Map.of("messages", List.of()),
                        "trajectory", Map.of("response_text", "pong")
                )),
                "",
                "session_done"
        );

        assertNotNull(finalized.get("sample_id"));
    }

    private static final class FakeRecorder implements SampleRecordingSink {
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

    private static final class FakeScorer implements JudgeScorer {
        private final Map<String, Object> result;
        private final List<Map<String, Object>> calls = new ArrayList<>();

        private FakeScorer(Map<String, Object> result) {
            this.result = result;
        }

        @Override
        public CompletableFuture<Map<String, Object>> score(String responseText, String instructionText, String followupUserFeedback,
                                                            String sessionId, int turnNum) {
            calls.add(Map.of(
                    "response_text", responseText,
                    "instruction_text", instructionText,
                    "followup_user_feedback", followupUserFeedback,
                    "session_id", sessionId,
                    "turn_num", turnNum
            ));
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class FakePendingJudgeBackend implements PendingJudgeStoreBackend {
        private final Map<String, byte[]> kv = new LinkedHashMap<>();
        private final Map<String, LinkedHashMap<String, Double>> zsets = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSeconds) {
            kv.put(key, value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            LinkedHashMap<String, Double> set = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            set.putAll(mapping);
            return mapping.size();
        }

        @Override
        public long expire(String key, int ttlSeconds) {
            return 1;
        }

        @Override
        public List<Object> zrange(String key, int start, int end) {
            return zsets.getOrDefault(key, new LinkedHashMap<>()).entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .map(value -> (Object) value)
                    .toList();
        }

        @Override
        public List<Object> mget(List<String> keys) {
            return keys.stream().map(kv::get).map(value -> (Object) value).toList();
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

        @Override
        public PendingJudgeStorePipeline pipeline() {
            return new PendingJudgeStorePipeline() {
                @Override
                public PendingJudgeStorePipeline delete(String key) {
                    kv.remove(key);
                    return this;
                }

                @Override
                public PendingJudgeStorePipeline zrem(String key, Object member) {
                    zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).remove(String.valueOf(member));
                    return this;
                }

                @Override
                public List<Object> execute() {
                    return List.of();
                }
            };
        }
    }
}
