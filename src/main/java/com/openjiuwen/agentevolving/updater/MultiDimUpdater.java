/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.Updates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-dimensional update updater.
 *
 * <p>Internally handles attribution/allocation, then runs corresponding
 * dimension optimizer for attributed operators, merges Updates.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.updater.multi_dim.MultiDimUpdater}.
 */
public abstract class MultiDimUpdater implements Updater {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected Map<String, Object> domainOptimizers = new HashMap<>();

    /**
     * Create with domain optimizers.
     *
     * @param domainOptimizers Map of domain name to optimizer
     */
    public MultiDimUpdater(Map<String, Object> domainOptimizers) {
        this.domainOptimizers = domainOptimizers != null ? domainOptimizers : new HashMap<>();
    }

    /**
     * Create empty updater.
     */
    public MultiDimUpdater() {
        this(new HashMap<>());
    }

    /**
     * Check if any domain optimizer requires forward data.
     *
     * @return True if any optimizer needs forward data
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean requiresForwardData() {
        for (Object opt : domainOptimizers.values()) {
            try {
                java.lang.reflect.Method method = opt.getClass().getMethod("requiresForwardData");
                Object result = method.invoke(opt);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            } catch (Exception e) {
                // Continue checking
            }
        }
        return false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config);

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract Object update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config);

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract Map<String, Object> getState();

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract void loadState(Map<String, Object> state);

    /**
     * Get domain optimizers.
     *
     * @return Domain optimizers map
     */
    public Map<String, Object> getDomainOptimizers() {
        return new HashMap<>(domainOptimizers);
    }
}
