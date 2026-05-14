/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * No-op rollout store when persistence is disabled.
 * <p>
 * Mirrors Python's {@code NullRolloutStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.null_store}.
 */
public class NullRolloutStore {

    public void saveRollout(int step, String taskId, Object rollout, String phase) {
        // no-op
    }

    public void saveStepSummary(int step, Map<String, Object> metrics) {
        // no-op
    }

    public List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit) {
        return Collections.emptyList();
    }

    public void close() {
        // no-op
    }
}
