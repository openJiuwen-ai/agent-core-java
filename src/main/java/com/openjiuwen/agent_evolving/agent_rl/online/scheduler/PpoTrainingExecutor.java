/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import java.util.List;
import java.util.Map;

/**
 * Minimal PPO batch training seam for the online scheduler.
 * <p>
 * Mirrors Python's {@code PPOTrainingExecutor} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.scheduler.ppo_executor}.
 */
public interface PpoTrainingExecutor {

    String trainBatch(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot);
}
