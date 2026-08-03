/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.FromEval;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Base class for multi-dimensional update generation.
 *
 * <p>Mirrors Python's {@code MultiDimUpdater} in
 * {@code openjiuwen/agent_evolving/updater/multi_dim.py}.</p>
 */
public abstract class MultiDimUpdater implements Updater {

    private final Map<String, BaseOptimizer> domainOptimizers;

    protected MultiDimUpdater() {
        this(null);
    }

    protected MultiDimUpdater(Map<String, ? extends BaseOptimizer> domainOptimizers) {
        this.domainOptimizers = domainOptimizers == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(domainOptimizers);
    }

    protected Map<String, BaseOptimizer> getDomainOptimizers() {
        return new LinkedHashMap<>(domainOptimizers);
    }

    @Override
    public boolean requiresForwardData() {
        for (BaseOptimizer optimizer : domainOptimizers.values()) {
            if (optimizer != null && optimizer.requiresForwardData()) {
                return true;
            }
        }
        return false;
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
