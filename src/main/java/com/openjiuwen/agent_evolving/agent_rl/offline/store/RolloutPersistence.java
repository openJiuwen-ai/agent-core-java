/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import java.util.List;
import java.util.Map;

/**
 * Abstract interface for persisting rollout trajectories
 * and per-step summaries to local file storage.
 * <p>
 * Mirrors Python's {@code RolloutPersistence} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.base}.
 */
public interface RolloutPersistence {

    /**
     * Persist a single rollout with its complete trajectory.
     * 
     * @param step Current training step
     * @param taskId Task identifier
     * @param rollout The rollout message to persist
     * @param phase "train" or "val" -- determines output sub-directory
     */
    void saveRollout(int step, String taskId, Object rollout, String phase);

    /**
     * Persist per-step training summary metrics.
     * 
     * @param step Current training step
     * @param metrics Metrics to persist
     */
    void saveStepSummary(int step, Map<String, Object> metrics);

    /**
     * Query historical rollouts by filters (for analysis/debugging).
     * 
     * @param filters Query filters
     * @param limit Maximum results to return
     * @return List of matching rollouts
     */
    List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit);

    /**
     * Release connections and clean up resources.
     */
    void close();
}