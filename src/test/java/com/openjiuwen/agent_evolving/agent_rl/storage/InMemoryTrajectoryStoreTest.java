/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void saveSampleRequiresSampleId() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.saveSample(new LinkedHashMap<>(), "online"));
        assertEquals("sample_id is required", error.getMessage());
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
        return sample;
    }
}
