/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer;

import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.Operator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Common skeleton for dimension-specific optimizers.
 *
 * <p>Mirrors Python's {@code BaseOptimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/base.py}.</p>
 */
public abstract class BaseOptimizer implements AutoCloseable {

    protected String domain = "";
    protected Map<String, Operator> operators = new LinkedHashMap<>();
    protected Map<String, TextualParameter> parameters = new LinkedHashMap<>();
    protected List<String> targets = new ArrayList<>();
    protected List<Trajectory> trajectories = new ArrayList<>();
    protected List<EvolutionSignal> selectedSignals = new ArrayList<>();

    /**
     * Whether this optimizer needs framework to execute forward on train cases.
     *
     * @return {@code true} by default
     */
    public boolean requiresForwardData() {
        return true;
    }

    /**
     * Enter the optimizer scope.
     *
     * @return current optimizer instance
     */
    public BaseOptimizer enter() {
        return this;
    }

    /**
     * Async enter compatibility for translated callers.
     *
     * @return completed stage with the current optimizer instance
     */
    public CompletionStage<BaseOptimizer> aenter() {
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Async exit compatibility for translated callers.
     *
     * @return completed stage with {@code null}
     */
    public CompletionStage<Void> aexit(Throwable excType, Throwable excVal, Throwable excTb) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Subclasses can override to provide default optimization targets.
     *
     * @return default targets list
     */
    public List<String> defaultTargets() {
        return List.of();
    }

    /**
     * Filter operators that expose any of the targets.
     *
     * @param operators operators keyed by operator id
     * @param targets optimization targets
     * @return matching operators
     */
    public static Map<String, Operator> filterOperators(Map<String, Operator> operators, List<String> targets) {
        Map<String, Operator> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Operator> entry : (operators == null ? Map.<String, Operator>of() : operators).entrySet()) {
            String operatorId = entry.getKey();
            Operator operator = entry.getValue();
            Map<String, ?> tunables = operator.getTunables();
            List<String> matched = new ArrayList<>();
            for (String target : targets) {
                if (tunables.containsKey(target)) {
                    matched.add(target);
                }
            }
            if (matched.isEmpty()) {
                Loggers.AGENT.warning("[optimizer] operator " + operatorId + " has no tunables in targets=" + targets);
                continue;
            }
            filtered.put(operatorId, operator);
        }
        return filtered;
    }

    /**
     * Filter and bind optimizable operators.
     *
     * @param operators operators keyed by operator id
     * @param targets optimization targets
     * @param config optimizer-specific config, currently unused here
     * @return number of bound operators
     */
    public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
        Map<String, Operator> resolvedOperators = operators == null ? Map.of() : operators;
        this.targets = new ArrayList<>(targets == null || targets.isEmpty() ? defaultTargets() : targets);
        this.operators = filterOperators(resolvedOperators, this.targets);
        this.parameters = new LinkedHashMap<>();
        for (String operatorId : this.operators.keySet()) {
            this.parameters.put(operatorId, new TextualParameter(operatorId));
        }
        this.trajectories = new ArrayList<>();
        this.selectedSignals = new ArrayList<>();
        if (this.operators.isEmpty()) {
            Loggers.AGENT.error("[optimizer] no operator matches targets=" + this.targets + "; will soft-exit");
        }
        return this.operators.size();
    }

    /**
     * Cache one trajectory for the backward phase.
     *
     * @param trajectory trajectory to cache
     */
    public void addTrajectory(Trajectory trajectory) {
        this.trajectories.add(trajectory);
    }

    /**
     * Return a snapshot of cached trajectories.
     *
     * @return copied trajectory list
     */
    public List<Trajectory> getTrajectories() {
        return new ArrayList<>(trajectories);
    }

    /**
     * Clear cached trajectories after a step.
     */
    public void clearTrajectories() {
        trajectories.clear();
    }

    /**
     * Run backward over the selected signals.
     *
     * @param signals evolution signals
     * @return completion stage for the backward pass
     */
    public CompletionStage<Void> backward(List<EvolutionSignal> signals) {
        validateParameters();
        List<EvolutionSignal> resolvedSignals = new ArrayList<>(signals == null ? List.of() : signals);
        this.selectedSignals = new ArrayList<>(selectSignals(resolvedSignals));
        try {
            return doBackward(resolvedSignals).handle((ignored, throwable) -> {
                if (throwable != null) {
                    throw wrapFailure(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR, throwable);
                }
                return null;
            });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(
                    wrapFailure(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR, exception)
            );
        }
    }

    /**
     * Execute one optimization step and return update mappings.
     *
     * @return generated updates
     */
    public Updates step() {
        validateParameters();
        try {
            Updates updates = doStep();
            clearTrajectories();
            return updates == null ? new Updates() : updates;
        } catch (Exception exception) {
            clearTrajectories();
            throw wrapFailure(StatusCode.TOOLCHAIN_OPTIMIZER_UPDATE_EXECUTION_ERROR, exception);
        }
    }

    /**
     * Expose the current parameter map.
     *
     * @return shallow copy of parameters
     */
    public Map<String, TextualParameter> parameters() {
        return new LinkedHashMap<>(parameters);
    }

    /**
     * Select consumable signals for this optimizer.
     *
     * @param signals source signals
     * @return selected signals
     */
    protected List<EvolutionSignal> selectSignals(List<EvolutionSignal> signals) {
        return new ArrayList<>(signals);
    }

    protected void validateParameters() {
        if (parameters.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR, "error_msg", "cannot optimize empty parameters");
        }
    }

    protected BaseError wrapFailure(StatusCode statusCode, Throwable throwable) {
        String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
        return ErrorHelper.buildError(statusCode, message, null, throwable, Map.of("error_msg", message));
    }

    protected abstract CompletionStage<Void> doBackward(List<EvolutionSignal> signals);

    protected abstract Updates doStep();

    @Override
    public void close() {
        // Python context manager exit is a no-op.
    }
}
