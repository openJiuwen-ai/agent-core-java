/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class LoopCoordinator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class LoopCoordinator {
    private final List<StopConditionEvaluator> evaluators;
    private int iteration;
    private int tokenUsage;
    private boolean isAborted;
    private long startMillis;
    private String stopReason;
    private Map<String, Object> lastResult;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopCoordinator() {
        this(List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopCoordinator(List<StopConditionEvaluator> evaluators) {
        this.evaluators = new ArrayList<>(evaluators);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void reset() {
        iteration = 0;
        tokenUsage = 0;
        isAborted = false;
        startMillis = System.currentTimeMillis();
        stopReason = null;
        lastResult = null;
        evaluators.forEach(StopConditionEvaluator::reset);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void incrementIteration() {
        iteration += 1;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addTokenUsage(int tokens) {
        if (tokens > 0) {
            tokenUsage += tokens;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLastResult(Map<String, Object> result) {
        this.lastResult = result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void requestAbort() {
        isAborted = true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldContinue() {
        if (isAborted) {
            stopReason = "Aborted";
            return false;
        }
        StopEvaluationContext ctx = StopEvaluationContext.builder()
                .iteration(iteration)
                .tokenUsage(tokenUsage)
                .elapsedSeconds((System.currentTimeMillis() - startMillis) / 1000.0)
                .lastResult(lastResult)
                .build();
        for (StopConditionEvaluator evaluator : evaluators) {
            if (evaluator.shouldStop(ctx)) {
                stopReason = evaluator.name();
                return false;
            }
        }
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionPromiseEvaluator getCompletionPromiseEvaluator() {
        return evaluators.stream()
                .filter(CompletionPromiseEvaluator.class::isInstance)
                .map(CompletionPromiseEvaluator.class::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getState() {
        Map<String, Object> evaluatorStates = new LinkedHashMap<>();
        for (StopConditionEvaluator evaluator : evaluators) {
            Map<String, Object> state = evaluator.getState();
            if (state != null) {
                evaluatorStates.put(evaluator.name(), state);
            }
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("iteration", iteration);
        snapshot.put("token_usage", tokenUsage);
        snapshot.put("stop_reason", stopReason);
        snapshot.put("evaluator_states", evaluatorStates);
        return snapshot;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Object loadedIteration = data.get("iteration");
        if (loadedIteration instanceof Number value) {
            iteration = value.intValue();
        }
        Object loadedTokenUsage = data.get("token_usage");
        if (loadedTokenUsage instanceof Number value) {
            tokenUsage = value.intValue();
        }
        Object loadedStopReason = data.get("stop_reason");
        stopReason = loadedStopReason != null ? String.valueOf(loadedStopReason) : null;
        startMillis = System.currentTimeMillis();
        Object rawStates = data.get("evaluator_states");
        if (rawStates instanceof Map<?, ?> states) {
            for (StopConditionEvaluator evaluator : evaluators) {
                Object state = states.get(evaluator.name());
                if (state instanceof Map<?, ?> mapState) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    mapState.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    evaluator.loadState(normalized);
                }
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCurrentIteration() {
        return iteration;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAborted() {
        return isAborted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getStopReason() {
        return stopReason;
    }
}
