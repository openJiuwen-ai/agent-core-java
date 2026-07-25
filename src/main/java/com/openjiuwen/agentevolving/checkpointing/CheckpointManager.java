/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import java.util.Map;

/**
 * Protocol interface for checkpoint management.
 *
 * <p>Mirrors Python's {@code CheckpointManager} in
 * {@code openjiuwen/agent_evolving/checkpointing/manager.py}.</p>
 */
public interface CheckpointManager {

    boolean shouldSave(int epoch, boolean improved);

    EvolveCheckpoint buildCheckpoint(Object agent, Object progress, Map<String, Object> updaterState);

    Map<String, Object> restore(Object agent, EvolveCheckpoint checkpoint);
}
