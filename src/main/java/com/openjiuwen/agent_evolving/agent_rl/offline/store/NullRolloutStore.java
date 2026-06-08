/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * No-op rollout store used when persistence is disabled.
 *
 * <p>Mirrors Python's {@code NullRolloutStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/null_store.py}.
 */
public class NullRolloutStore implements RolloutPersistence {

    @Override
    public void saveRollout(int step, String taskId, RolloutMessage rollout, String phase) {
        // no-op
    }

    @Override
    public void saveStepSummary(int step, Map<String, Object> metrics) {
        // no-op
    }

    @Override
    public List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit) {
        return Collections.emptyList();
    }

    @Override
    public void close() {
        // no-op
    }
}
