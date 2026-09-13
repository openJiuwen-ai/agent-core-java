/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Common skeleton for dimension-specific optimizers.
 * <p>
 * bind(): Filters optimizable Operators, returns count (0 triggers soft-exit).
 * add_trajectory / get_trajectories: Caches Trajectory for backward.
 * step(): Returns Updates, applied by Trainer.apply_updates.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.base.BaseOptimizer}.
 * 
 * @since 0.1.7
 */
public abstract class BaseOptimizer {
    /**
     * domain.
     * 
     * @since 0.1.7
     */
    protected String domain = "";

    /**
     * operators.
     * 
     * @since 0.1.7
     */
    protected Map<String, Object> operators = new HashMap<>();

    /**
     * parameters.
     * 
     * @since 0.1.7
     */
    protected Map<String, TextualParameter> parameters = new HashMap<>();

    /**
     * targets.
     * 
     * @since 0.1.7
     */
    protected List<String> targets = new ArrayList<>();

    /**
     * trajectories.
     * 
     * @since 0.1.7
     */
    protected List<Trajectory> trajectories = new ArrayList<>();

    /**
     * badCases.
     * 
     * @since 0.1.7
     */
    protected List<EvaluatedCase> badCases = new ArrayList<>();

    /**
     * Whether this optimizer needs framework to execute forward on train_cases.
     * 
     * @return True (default): optimizer uses trajectories/evaluated_cases from forward.
     * @since 0.1.7
     */
    public boolean requiresForwardData() {
        return true;
    }

    /**
     * Subclass can override to provide default target list for this dimension.
     * 
     * @return Default targets list
     * @since 0.1.7
     */
    public List<String> defaultTargets() {
        return Collections.emptyList();
    }

    /**
     * Filter Operators that expose any of the targets.
     * 
     * @param operators Operators map
     * @param targets Target list
     * @return Filtered operators
     * @since 0.1.7
     */
    public static Map<String, Object> filterOperators(Map<String, Object> operators, List<String> targets) {
        Map<String, Object> result = new HashMap<>();
        if (operators == null || targets == null || targets.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : operators.entrySet()) {
            String opId = entry.getKey();
            Object op = entry.getValue();
            try {
                Set<String> tunableNames = extractTunableNames(op);
                boolean matched = false;
                for (String target : targets) {
                    if (tunableNames.contains(target)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    result.put(opId, op);
                } else {
                    Loggers.AGENT.warn("[optimizer] operator {} has no tunables in targets={}", opId, targets);
                }
            } catch (ReflectiveOperationException e) {
                Loggers.AGENT.warn("[optimizer] operator {} does not support getTunables", opId);
            }
        }
        return result;
    }

    /**
     * Filter and bind optimizable Operators.
     * 
     * @param operators Operators map
     * @param targets Target list
     * @param config Configuration map
     * @return Count of bound operators (0 triggers soft-exit)
     * @since 0.1.7
     */
    public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
        if (operators == null) {
            operators = new HashMap<>();
        }
        this.targets = targets != null ? new ArrayList<>(targets) : defaultTargets();
        this.operators = filterOperators(operators, this.targets);
        this.parameters = new HashMap<>();
        for (String opId : this.operators.keySet()) {
            this.parameters.put(opId, new TextualParameter(opId));
        }
        this.trajectories = new ArrayList<>();
        this.badCases = new ArrayList<>();

        if (this.operators.isEmpty()) {
            Loggers.AGENT.error("[optimizer] no operator matches targets={}; will soft-exit", this.targets);
        }
        return this.operators.size();
    }

    /**
     * Cache Trajectory for backward phase query.
     * 
     * @param trajectory Trajectory to add
     * @since 0.1.7
     */
    public void addTrajectory(Trajectory trajectory) {
        trajectories.add(trajectory);
    }

    /**
     * Returns currently cached trajectory list.
     * 
     * @return List of trajectories
     * @since 0.1.7
     */
    public List<Trajectory> getTrajectories() {
        return new ArrayList<>(trajectories);
    }

    /**
     * Clear trajectory cache after update.
     * 
     * @since 0.1.7
     */
    public void clearTrajectories() {
        trajectories.clear();
    }

    /**
     * Execute backward pass.
     * 
     * @param evaluatedCases Evaluated cases
     * @since 0.1.7
     */
    public void backward(List<EvaluatedCase> evaluatedCases) {
        validateParameters();
        getBadCases(evaluatedCases != null ? evaluatedCases : Collections.emptyList());
        try {
            doBackward(evaluatedCases != null ? evaluatedCases : Collections.emptyList());
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR, e.getMessage(), null,
                    e, Map.of("error_msg", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * Execute step and return Updates.
     * 
     * @return Updates to apply
     * @since 0.1.7
     */
    public Updates step() {
        validateParameters();
        try {
            Updates updates = doStep();
            clearTrajectories();
            return updates != null ? updates : new Updates();
        } catch (Exception e) {
            clearTrajectories();
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_OPTIMIZER_UPDATE_EXECUTION_ERROR, e.getMessage(), null, e,
                    Map.of("error_msg", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * Subclass implements: generates Updates based on gradients written during backward.
     * 
     * @return Updates
     * @since 0.1.7
     */
    protected abstract Updates doStep();

    /**
     * Subclass implements: backward pass logic.
     * 
     * @param evaluatedCases Evaluated cases
     * @since 0.1.7
     */
    protected abstract void doBackward(List<EvaluatedCase> evaluatedCases);

    /**
     * Get parameters map.
     * 
     * @return Copy of parameters
     * @since 0.1.7
     */
    public Map<String, TextualParameter> parameters() {
        return new HashMap<>(parameters);
    }

    /**
     * Get cases with score == 0.
     * 
     * @param evaluatedCases All evaluated cases
     * @return Filtered list of bad cases
     * @since 0.1.7
     */
    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        badCases = (evaluatedCases != null ? evaluatedCases : Collections.<EvaluatedCase>emptyList()).stream()
                .filter(c -> c.getScore() == 0.0).collect(Collectors.toList());
        return badCases;
    }

    /**
     * Validate parameters are not empty.
     * 
     * @since 0.1.7
     */
    protected void validateParameters() {
        if (parameters.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR, "error_msg",
                    "cannot optimize empty parameters");
        }
    }

    /**
     * Get domain name.
     * 
     * @return Domain string
     * @since 0.1.7
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Get operators map.
     * 
     * @return Operators map
     * @since 0.1.7
     */
    public Map<String, Object> getOperators() {
        return operators;
    }

    /**
     * Get bad cases.
     * 
     * @return Bad cases list
     * @since 0.1.7
     */
    public List<EvaluatedCase> getBadCases() {
        return badCases;
    }

    /**
     * extractTunableNames.
     * 
     * @param operator operator
     * @return the result
     * @throws ReflectiveOperationException ReflectiveOperationException
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected static Set<String> extractTunableNames(Object operator) throws ReflectiveOperationException {
        if (operator == null) {
            return Collections.emptySet();
        }
        java.lang.reflect.Method method = operator.getClass().getMethod("getTunables");
        Object tunables = method.invoke(operator);
        if (tunables instanceof Map<?, ?> map) {
            Set<String> keys = new LinkedHashSet<>();
            for (Object key : map.keySet()) {
                keys.add(String.valueOf(key));
            }
            return keys;
        }
        if (tunables instanceof Collection<?> collection) {
            Set<String> keys = new LinkedHashSet<>();
            for (Object item : collection) {
                keys.add(String.valueOf(item));
            }
            return keys;
        }
        if (tunables != null && tunables.getClass().isArray()) {
            Set<String> keys = new LinkedHashSet<>();
            Object[] array = (Object[]) tunables;
            for (Object item : array) {
                keys.add(String.valueOf(item));
            }
            return keys;
        }
        return Collections.emptySet();
    }
}
