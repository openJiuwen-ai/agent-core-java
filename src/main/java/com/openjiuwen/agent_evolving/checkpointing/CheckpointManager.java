// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.Map;

/**
 * Protocol interface for checkpoint management.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.manager.CheckpointManager}.
 */
public interface CheckpointManager {

    /**
     * Determine whether checkpoint should be saved.
     *
     * @param epoch    Current epoch number
     * @param improved Whether validation score improved
     * @return True if checkpoint should be saved
     */
    boolean shouldSave(int epoch, boolean improved);

    /**
     * Build checkpoint from agent and progress state.
     *
     * @param agent        Agent instance
     * @param progress     Progress tracker
     * @param updaterState Optional updater state
     * @return Built checkpoint
     */
    EvolveCheckpoint buildCheckpoint(Object agent, Object progress, Map<String, Object> updaterState);

    /**
     * Restore agent's operators_state, return progress_state.
     *
     * @param agent     Agent instance
     * @param checkpoint Checkpoint to restore from
     * @return Progress state for Trainer progress recovery
     */
    Map<String, Object> restore(Object agent, EvolveCheckpoint checkpoint);
}