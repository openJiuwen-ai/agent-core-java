/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.trajectory.Trajectory;

import java.util.List;
import java.util.Map;

/**
 * Updater protocol definition.
 * <p>
 * Core convergence point: Unifies "single-dimension optimizer" and
 * "multi-dimensional attribution + allocation" into one interface.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.updater.protocol.Updater}.
 * 
 * @since 0.1.7
 */
public interface Updater {
    /**
     * bind.
     * 
     * @param operators operators
     * @param targets targets
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config);

    /**
     * Whether this updater needs framework to execute forward on train_cases.
     * 
     * @return True for standard optimizers, False for black-box optimizers
     * @since 0.1.7
     */
    boolean requiresForwardData();

    /**
     * Generate updates from trajectories and evaluated cases.
     * 
     * @param trajectories List of trajectories
     * @param evaluatedCases Evaluated cases
     * @param config Configuration map
     * @return Updates or list of candidate Updates
     * @since 0.1.7
     */
    Object update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config);

    /**
     * Get updater state for checkpointing.
     * 
     * @return State map
     * @since 0.1.7
     */
    Map<String, Object> getState();

    /**
     * Load updater state from checkpoint.
     * 
     * @param state State map
     * @since 0.1.7
     */
    void loadState(Map<String, Object> state);
}
