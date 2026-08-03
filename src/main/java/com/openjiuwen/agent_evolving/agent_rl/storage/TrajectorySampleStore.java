/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import java.util.List;
import java.util.Map;

/**
 * Shared RL trajectory sample store contract.
 * <p>
 * Mirrors Python's {@code TrajectorySampleStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/storage/trajectory_store.py}.
 */
public interface TrajectorySampleStore {

    void saveSample(Map<String, Object> sample, String userId);

    int getPendingCount(String userId);

    List<String> getUsersAboveThreshold(int threshold);

    List<Map<String, Object>> fetchAndMarkTraining(String userId, int limit);

    void markTrained(List<String> sampleIds);

    void markFailed(List<String> sampleIds);

    void resetToPending(List<String> sampleIds);

    Map<String, Integer> stats();
}
