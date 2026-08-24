/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.core.operator.Operator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Core convergence point for updater implementations.
 *
 * <p>Mirrors Python's {@code Updater} protocol in
 * {@code openjiuwen/agent_evolving/updater/protocol.py}.</p>
 *
 * <p>The Python contract returns either one update mapping or a list of update
 * mappings keyed by {@code (operator_id, target)} tuples. Java keeps that
 * return value as {@code Object} at this protocol boundary because the union is
 * part of the public contract; implementations should use
 * {@code Map<UpdateKey, Object>} or {@code List<Map<UpdateKey, Object>>}.</p>
 */
public interface Updater {

    /**
     * Bind operators and optionally filter optimization targets.
     *
     * @param operators operators keyed by operator id
     * @param targets optional target-name filter, may be {@code null}
     * @param config updater-specific dynamic configuration
     * @return count of bound operators; {@code 0} triggers the Python soft-exit behavior upstream
     */
    int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config);

    /**
     * Whether the updater needs framework forward-pass data.
     *
     * @return {@code true} when train-case forward data is required
     */
    boolean requiresForwardData();

    /**
     * Produce update mappings from trajectories and evaluated cases.
     *
     * @param trajectories trajectory inputs
     * @param evaluatedCases evaluated cases from the trainer pipeline
     * @param config updater-specific dynamic configuration
     * @return completion stage resolving to one mapping or a list of mappings
     */
    CompletionStage<Object> update(
            List<Trajectory> trajectories,
            List<Object> evaluatedCases,
            Map<String, Object> config
    );

    /**
     * Produce update mappings from trajectories and evolution signals.
     *
     * @param trajectories trajectory inputs
     * @param signals evolution signals
     * @param config updater-specific dynamic configuration
     * @return completion stage resolving to one mapping or a list of mappings
     */
    CompletionStage<Object> process(
            List<Trajectory> trajectories,
            List<EvolutionSignal> signals,
            Map<String, Object> config
    );

    /**
     * Snapshot updater state for checkpointing.
     *
     * @return serializable state payload
     */
    Map<String, Object> getState();

    /**
     * Restore updater state from a checkpoint payload.
     *
     * @param state serialized state payload
     */
    void loadState(Map<String, Object> state);
}
