/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.FromEval;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.operator.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Single-dimension updater backed by one optimizer.
 *
 * <p>Mirrors Python's {@code SingleDimUpdater} in
 * {@code openjiuwen/agent_evolving/updater/single_dim.py}.</p>
 */
public final class SingleDimUpdater implements Updater {

    private final BaseOptimizer optimizer;

    public SingleDimUpdater(BaseOptimizer optimizer) {
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
    }

    @Override
    public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
        Map<String, Object> resolvedConfig = Objects.requireNonNull(config, "config");
        List<String> effectiveTargets = targets == null || targets.isEmpty()
                ? readTargets(resolvedConfig.get("targets"))
                : targets;
        return optimizer.bind(operators, effectiveTargets, resolvedConfig);
    }

    @Override
    public boolean requiresForwardData() {
        return optimizer.requiresForwardData();
    }

    @Override
    public CompletionStage<Object> process(
            List<Trajectory> trajectories,
            List<EvolutionSignal> signals,
            Map<String, Object> config
    ) {
        Objects.requireNonNull(trajectories, "trajectories");
        for (Trajectory trajectory : trajectories) {
            optimizer.addTrajectory(trajectory);
        }
        return optimizer.backward(signals).thenApply(ignored -> optimizer.step());
    }

    @Override
    public CompletionStage<Object> update(
            List<Trajectory> trajectories,
            List<Object> evaluatedCases,
            Map<String, Object> config
    ) {
        Objects.requireNonNull(evaluatedCases, "evaluatedCases");
        Map<String, Object> resolvedConfig = Objects.requireNonNull(config, "config");
        Double scoreThreshold = readScoreThreshold(resolvedConfig.get("score_threshold"));
        List<EvolutionSignal> signals = new ArrayList<>();
        for (Object caseValue : evaluatedCases) {
            if (!(caseValue instanceof EvaluatedCase evaluatedCase)) {
                throw new IllegalArgumentException("evaluatedCases must contain EvaluatedCase values");
            }
            EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluatedCase, "", scoreThreshold);
            if (signal != null) {
                signals.add(signal);
            }
        }
        return process(trajectories, signals, resolvedConfig);
    }

    @Override
    public Map<String, Object> getState() {
        return Map.of();
    }

    @Override
    public void loadState(Map<String, Object> state) {
    }

    private static List<String> readTargets(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("targets must be a list");
        }
        List<String> targets = new ArrayList<>();
        for (Object item : values) {
            targets.add(String.valueOf(item));
        }
        return targets;
    }

    private static Double readScoreThreshold(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("score_threshold must be numeric");
    }
}
