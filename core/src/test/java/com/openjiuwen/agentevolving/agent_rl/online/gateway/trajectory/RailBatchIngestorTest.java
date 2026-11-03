/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailBatchIngestorTest {

    @Test
    void normalizeRailSampleBuildsRailPayloadWithStableUserId() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", "session-1");
        payload.put("trajectory_id", "traj-1");
        payload.put("trajectory_meta", Map.of("source", "gateway"));
        payload.put("tenant_id", "tenant-1");
        payload.put("model_id", "model-a");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        sample.put("response", Map.of(
                "content", "pong",
                "usage", Map.of("total_tokens", 3),
                "finish_reason", "stop",
                "tool_calls", List.of()
        ));
        sample.put("prompt_ids", List.of(1, 2));
        sample.put("response_tokens", List.of(3, 4));
        sample.put("logprobs", List.of(-0.5, -0.25));
        sample.put("meta", Map.of("kind", "test"));

        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(payload, sample, "");

        assertEquals("traj-1:0", normalized.get("sample_id"));
        assertEquals("tenant-1", normalized.get("user_id"));
        assertEquals("session-1", normalized.get("session_id"));
        assertEquals(1, normalized.get("turn_num"));
        assertEquals("traj-1", normalized.get("trajectory_id"));
        assertEquals(0, normalized.get("step_index"));
        @SuppressWarnings("unchecked")
        Map<String, Object> trajectory = (Map<String, Object>) normalized.get("trajectory");
        assertEquals(List.of(1, 2), trajectory.get("prompt_ids"));
        assertEquals(List.of(3, 4), trajectory.get("response_ids"));
        assertEquals(List.of(-0.5, -0.25), trajectory.get("response_logprobs"));
        @SuppressWarnings("unchecked")
        Map<String, Object> railMeta = (Map<String, Object>) normalized.get("rail_meta");
        assertEquals("hello", railMeta.get("instruction_text"));
        assertEquals(Map.of("source", "gateway"), railMeta.get("trajectory_meta"));
    }

    @Test
    void normalizeRailSampleFallsBackFromPythonFalsyValues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", "session-from-payload");
        payload.put("trajectory_id", "traj-from-payload");
        payload.put("tenant_id", "tenant-from-payload");
        payload.put("model_id", "model-from-payload");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("session_id", 0);
        sample.put("trajectory_id", 0);
        sample.put("user_id", 0);
        sample.put("prompt_text", 0);
        sample.put("response_text", 0);
        sample.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        sample.put("response", Map.of(
                "content", "response-from-map",
                "usage", Map.of(),
                "finish_reason", "stop",
                "tool_calls", List.of()
        ));
        sample.put("prompt_ids", List.of(1));
        sample.put("response_tokens", List.of(2));

        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(payload, sample, "");

        assertEquals("session-from-payload", normalized.get("session_id"));
        assertEquals("traj-from-payload", normalized.get("trajectory_id"));
        assertEquals("tenant-from-payload", normalized.get("user_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> trajectory = (Map<String, Object>) normalized.get("trajectory");
        assertEquals("", trajectory.get("prompt_text"));
        assertEquals("response-from-map", trajectory.get("response_text"));
    }

    @Test
    void ingestRailBatchStagesValidSamplesAndFlushesSession() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        RecordingJudgeDispatcher dispatcher = new RecordingJudgeDispatcher(store);
        RailBatchIngestor ingestor = new RailBatchIngestor(store, dispatcher, "fallback-user");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("protocol_version", "rail-v1");
        payload.put("session_id", "session-1");
        payload.put("trajectory_id", "traj-1");
        payload.put("prev_feedback", Map.of("text", "next turn"));
        payload.put("session_done", true);
        payload.put("samples", List.of(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello")),
                "response", Map.of("content", "pong", "usage", Map.of(), "tool_calls", List.of()),
                "prompt_ids", List.of(1, 2),
                "response_tokens", List.of(3),
                "logprobs", List.of(-0.1)
        )));

        Map<String, Object> result = ingestor.ingestRailBatch(payload);

        assertEquals(1, result.get("accepted"));
        assertEquals(0, result.get("rejected"));
        assertEquals(2, result.get("judged"));
        assertEquals(3, result.get("session_flushed"));
        assertTrue(backend.kv.containsKey("pending_judge:session-1:traj-1:0"));
    }

    @Test
    void ingestRailBatchRejectsAllInvalidSamples() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        RailBatchIngestor ingestor = new RailBatchIngestor(store, new RecordingJudgeDispatcher(store), "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("protocol_version", "rail-v1");
        payload.put("session_id", "session-1");
        payload.put("trajectory_id", "traj-1");
        payload.put("samples", List.of(Map.of("messages", List.of())));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ingestor.ingestRailBatch(payload)
        );

        assertTrue(exception.getMessage().contains("messages must be a non-empty list"));
    }

    private static final class RecordingJudgeDispatcher extends JudgeDispatcher {
        private int onPrevFeedbackCalls;
        private int onSessionDoneCalls;

        private RecordingJudgeDispatcher(PendingJudgeStore pendingStore) {
            super(pendingStore, sample -> { }, null);
        }

        @Override
        public int onPrevFeedback(String sessionId, Map<String, Object> prevFeedback) {
            onPrevFeedbackCalls += 1;
            return 2;
        }

        @Override
        public int onSessionDone(String sessionId) {
            onSessionDoneCalls += 1;
            return 3;
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
