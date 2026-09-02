/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.trajectory.Trajectory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-dimensional update updater.
 * <p>
 * Internally handles attribution/allocation, then runs corresponding
 * dimension optimizer for attributed operators, merges Updates.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.updater.multi_dim.MultiDimUpdater}.
 * 
 * @since 0.1.7
 */
public abstract class MultiDimUpdater implements Updater {
    /**
     * domainOptimizers.
     * 
     * @since 0.1.7
     */
    protected Map<String, Object> domainOptimizers = new HashMap<>();

    /**
     * Create with domain optimizers.
     * 
     * @param domainOptimizers Map of domain name to optimizer
     * @since 0.1.7
     */
    public MultiDimUpdater(Map<String, Object> domainOptimizers) {
        this.domainOptimizers = domainOptimizers != null ? domainOptimizers : new HashMap<>();
    }

    /**
     * Create empty updater.
     * 
     * @since 0.1.7
     */
    public MultiDimUpdater() {
        this(new HashMap<>());
    }

    /**
     * requiresForwardData.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
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

    /**
     * bind.
     * 
     * @param operators operators
     * @param targets targets
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    @Override
    public abstract int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config);

    /**
     * update.
     * 
     * @param trajectories trajectories
     * @param evaluatedCases evaluatedCases
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    @Override
    public abstract Object update(List<Trajectory> trajectories, List<Object> evaluatedCases,
            Map<String, Object> config);

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public abstract Map<String, Object> getState();

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
     */
    @Override
    public abstract void loadState(Map<String, Object> state);

    /**
     * Get domain optimizers.
     * 
     * @return Domain optimizers map
     * @since 0.1.7
     */
    public Map<String, Object> getDomainOptimizers() {
        return new HashMap<>(domainOptimizers);
    }
}
