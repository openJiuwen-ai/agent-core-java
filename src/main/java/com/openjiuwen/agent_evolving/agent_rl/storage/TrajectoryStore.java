/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Module-level facade for RL trajectory sample stores.
 * <p>
 * Mirrors Python's module in
 * {@code openjiuwen/agent_evolving/agent_rl/storage/trajectory_store.py}.
 */
public final class TrajectoryStore {

    private TrajectoryStore() {
    }

    public static TrajectorySampleStore inMemory() {
        return new InMemoryTrajectoryStore();
    }

    public static TrajectorySampleStore redis(RedisTrajectoryStoreBackend backend) {
        return new RedisTrajectoryStore(Objects.requireNonNull(backend, "backend"));
    }

    public static Map<String, Integer> zeroStats() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total_samples", 0);
        result.put("pending_samples", 0);
        result.put("training_samples", 0);
        result.put("trained_samples", 0);
        result.put("failed_samples", 0);
        return result;
    }
}
