/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.store;

import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;

import java.util.List;
import java.util.Map;

/**
 * Abstract interface for persisting rollout trajectories and per-step summaries to local storage.
 * <p>
 * Mirrors Python's {@code RolloutPersistence} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/base.py}.
 */
public interface RolloutPersistence {

    default void saveRollout(int step, String taskId, RolloutMessage rollout) {
        saveRollout(step, taskId, rollout, "train");
    }

    void saveRollout(int step, String taskId, RolloutMessage rollout, String phase);

    void saveStepSummary(int step, Map<String, Object> metrics);

    default List<Map<String, Object>> queryRollouts(Map<String, Object> filters) {
        return queryRollouts(filters, 100);
    }

    List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit);

    void close();
}
