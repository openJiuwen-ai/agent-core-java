/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import java.util.Map;

/**
 * Protocol interface for checkpoint management.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.manager.CheckpointManager}.
 * @since 0.1.7
 */
public interface CheckpointManager {
    boolean shouldSave(int epoch, boolean improved);

    /**
     * Build checkpoint from agent and progress state.
     * 
     * @param agent Agent instance
     * @param progress Progress tracker
     * @param updaterState Optional updater state
     * @return Built checkpoint
     * @since 0.1.7
     */
    EvolveCheckpoint buildCheckpoint(Object agent, Object progress, Map<String, Object> updaterState);

    /**
     * Restore agent's operators_state, return progress_state.
     * 
     * @param agent Agent instance
     * @param checkpoint Checkpoint to restore from
     * @return Progress state for Trainer progress recovery
     * @since 0.1.7
     */
    Map<String, Object> restore(Object agent, EvolveCheckpoint checkpoint);
}
