/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryTrajectoryStoreTest {

    @Test
    void statusFlowMatchesPythonSlice() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.saveSample(sample("s1"), "online");
        store.saveSample(sample("s2"), "online");

        assertEquals(2, store.getPendingCount("online"));
        assertEquals(List.of("online"), store.getUsersAboveThreshold(2));

        List<Map<String, Object>> samples = store.fetchAndMarkTraining("online", 2);
        assertEquals(List.of("s1", "s2"), samples.stream().map(item -> String.valueOf(item.get("sample_id"))).toList());

        store.markTrained(List.of("s1"));
        store.markFailed(List.of("s2"));

        Map<String, Integer> stats = store.stats();
        assertEquals(0, stats.get("pending_samples"));
        assertEquals(1, stats.get("trained_samples"));
        assertEquals(1, stats.get("failed_samples"));
    }

    @Test
    void saveSampleReplacesExistingStatusAndDeepCopiesPayload() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        Map<String, Object> sample = sample("s1");
        store.saveSample(sample, "online");
        store.fetchAndMarkTraining("online", 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) sample.get("request");
        request.put("messages", List.of(Map.of("role", "user", "content", "mutated-after-save")));

        store.saveSample(sample("s1"), "online");
        List<Map<String, Object>> reloaded = store.fetchAndMarkTraining("online", 1);

        assertEquals(1, store.stats().get("training_samples"));
        assertEquals(0, store.stats().get("pending_samples"));
        assertEquals("hello", firstMessageContent(reloaded.getFirst()));
    }

    @Test
    void resetToPendingMovesTrainingSampleBack() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.saveSample(sample("s1"), "online");
        store.fetchAndMarkTraining("online", 1);

        store.resetToPending(List.of("s1"));

        Map<String, Integer> stats = store.stats();
        assertEquals(1, stats.get("pending_samples"));
        assertEquals(0, stats.get("training_samples"));
        assertEquals(0, stats.get("failed_samples"));
    }

    @Test
    void saveSampleRequiresSampleId() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.saveSample(new LinkedHashMap<>(), "online"));
        assertEquals("sample_id is required", error.getMessage());
    }

    @Test
    void fetchReturnsDetachedCopies() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.saveSample(sample("s1"), "online");

        List<Map<String, Object>> firstFetch = store.fetchAndMarkTraining("online", 1);
        List<Map<String, Object>> secondFetch = store.fetchAndMarkTraining("online", 1);

        assertEquals(1, firstFetch.size());
        assertEquals(0, secondFetch.size());
        assertNotSame(firstFetch.getFirst(), sample("s1"));
    }

    private static Map<String, Object> sample(String sampleId) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", sampleId);
        sample.put("user_id", "online");
        sample.put("session_id", "sess-1");
        sample.put("created_at", "2026-01-01T00:00:00+00:00");
        sample.put("request", new LinkedHashMap<>(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello"))
        )));
        sample.put("response", Map.of("message", Map.of("role", "assistant", "content", "world")));
        sample.put("trajectory", Map.of(
                "input_ids", List.of(1, 2, 3),
                "response_ids", List.of(4, 5),
                "response_logprobs", List.of(-0.1, -0.2)
        ));
        sample.put("judge", Map.of("score", 0.5));
        return sample;
    }

    @SuppressWarnings("unchecked")
    private static String firstMessageContent(Map<String, Object> sample) {
        Map<String, Object> request = (Map<String, Object>) sample.get("request");
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        return String.valueOf(messages.getFirst().get("content"));
    }
}
