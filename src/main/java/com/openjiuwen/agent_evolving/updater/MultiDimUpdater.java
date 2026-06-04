/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;

import java.util.ArrayList;
import java.util.Collection;
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
    public boolean requiresForwardData() {
        for (Object opt : domainOptimizers.values()) {
            Object result = invokeRequiresForwardData(opt);
            if (pythonTruthy(result)) {
                return true;
            }
        }
        return false;
    }

    private Object invokeRequiresForwardData(Object optimizer) {
        if (optimizer == null) {
            return null;
        }
        for (String methodName : List.of("requires_forward_data", "requiresForwardData")) {
            try {
                java.lang.reflect.Method method = optimizer.getClass().getMethod(methodName);
                return method.invoke(optimizer);
            } catch (Exception ignored) {
                try {
                    java.lang.reflect.Method method = optimizer.getClass().getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(optimizer);
                } catch (Exception ignoredAgain) {
                    // Continue checking the next Python/Java method spelling.
                }
            }
        }
        return null;
    }

    private boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    @Override
    public abstract int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config);

    @Override
    public abstract Object update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config);

    @Override
    public abstract Map<String, Object> getState();

    @Override
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
