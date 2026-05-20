/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-dimension update updater.
 *
 * <p>Reuses BaseOptimizer (backward/step), Updates-first applied uniformly by Trainer.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.updater.single_dim.SingleDimUpdater}.
 */
public class SingleDimUpdater implements Updater {

    private final BaseOptimizer optimizer;

    /**
     * Create with optimizer.
     *
     * @param optimizer Base optimizer instance
     */
    public SingleDimUpdater(BaseOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
        List<String> effectiveTargets = targets;
        if (effectiveTargets == null && config != null) {
            @SuppressWarnings("unchecked")
            List<String> configTargets = (List<String>) config.get("targets");
            effectiveTargets = configTargets;
        }
        return optimizer.bind(operators, effectiveTargets, config != null ? config : new HashMap<>());
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean requiresForwardData() {
        return optimizer.requiresForwardData();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Updates update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config) {
        for (Trajectory traj : trajectories != null ? trajectories : List.<Trajectory>of()) {
            optimizer.addTrajectory(traj);
        }
        List<EvaluatedCase> typedCases = (evaluatedCases != null ? evaluatedCases : List.of()).stream()
                .filter(EvaluatedCase.class::isInstance)
                .map(EvaluatedCase.class::cast)
                .toList();
        optimizer.backward(typedCases);
        Updates updates = optimizer.step();
        return updates != null ? updates : new Updates();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getState() {
        // Current: BaseOptimizer has no stable recoverable state
        return new HashMap<>();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(Map<String, Object> state) {
        // No-op: BaseOptimizer has no stable recoverable state
    }
}
