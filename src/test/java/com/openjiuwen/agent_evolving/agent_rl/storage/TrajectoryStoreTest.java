/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrajectoryStoreTest {

    @Test
    void inMemoryFactoryReturnsUsableSampleStore() {
        TrajectorySampleStore store = TrajectoryStore.inMemory();

        store.saveSample(sample("s1"), "online");
        store.saveSample(sample("s2"), "online");

        assertInstanceOf(InMemoryTrajectoryStore.class, store);
        assertEquals(2, store.getPendingCount("online"));
        assertEquals(List.of("online"), store.getUsersAboveThreshold(2));
    }

    @Test
    void redisFactoryWrapsProvidedBackend() {
        TrajectorySampleStore store = TrajectoryStore.redis(new RedisTrajectoryStoreTest.FakeRedis());

        store.saveSample(sample("s1"), "online");

        assertInstanceOf(RedisTrajectoryStore.class, store);
        assertEquals(1, store.getPendingCount("online"));
    }

    @Test
    void redisFactoryRequiresBackend() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> TrajectoryStore.redis(null));

        assertEquals("backend", error.getMessage());
    }

    @Test
    void zeroStatsMatchesPythonStatsShape() {
        assertEquals(Map.of(
                "total_samples", 0,
                "pending_samples", 0,
                "training_samples", 0,
                "trained_samples", 0,
                "failed_samples", 0
        ), TrajectoryStore.zeroStats());
    }

    private static Map<String, Object> sample(String sampleId) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", sampleId);
        sample.put("user_id", "online");
        sample.put("session_id", "sess-1");
        sample.put("created_at", "2026-01-01T00:00:00+00:00");
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("response", Map.of("message", Map.of("role", "assistant", "content", "world")));
        sample.put("trajectory", Map.of(
                "input_ids", List.of(1, 2, 3),
                "response_ids", List.of(4, 5),
                "response_logprobs", List.of(-0.1, -0.2)
        ));
        sample.put("judge", Map.of("score", 0.5));
        return sample;
    }
}
