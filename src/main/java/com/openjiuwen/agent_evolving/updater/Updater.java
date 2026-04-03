// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;

import java.util.List;
import java.util.Map;

/**
 * Updater protocol definition.
 *
 * <p>Core convergence point: Unifies "single-dimension optimizer" and
 * "multi-dimensional attribution + allocation" into one interface.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.updater.protocol.Updater}.
 */
public interface Updater {

    /**
     * Bind operators and filter optimizable ones.
     *
     * @param operators Operators map
     * @param targets   Optional target list
     * @param config    Configuration map
     * @return Count of bound operators (0 triggers soft-exit)
     */
    int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config);

    /**
     * Whether this updater needs framework to execute forward on train_cases.
     *
     * @return True for standard optimizers, False for black-box optimizers
     */
    boolean requiresForwardData();

    /**
     * Generate updates from trajectories and evaluated cases.
     *
     * @param trajectories    List of trajectories
     * @param evaluatedCases  Evaluated cases
     * @param config          Configuration map
     * @return Updates or list of candidate Updates
     */
    Object update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config);

    /**
     * Get updater state for checkpointing.
     *
     * @return State map
     */
    Map<String, Object> getState();

    /**
     * Load updater state from checkpoint.
     *
     * @param state State map
     */
    void loadState(Map<String, Object> state);
}