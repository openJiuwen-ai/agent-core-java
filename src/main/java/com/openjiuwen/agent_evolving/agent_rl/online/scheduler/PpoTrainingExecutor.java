/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import java.util.List;
import java.util.Map;

/**
 * PPO batch training contract for the online scheduler.
 *
 * <p>Mirrors Python's {@code PPOTrainingExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/ppo_executor.py}.</p>
 */
public interface PpoTrainingExecutor {

    String trainBatch(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot);
}
