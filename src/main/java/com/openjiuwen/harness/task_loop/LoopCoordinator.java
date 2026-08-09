/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the DeepAgent outer task loop.
 *
 * <p>Mirrors Python's {@code LoopCoordinator} in
 * {@code openjiuwen/harness/task_loop/loop_coordinator.py}.</p>
 */
public final class LoopCoordinator {

    private static final Logger LOGGER = Logger.getLogger(LoopCoordinator.class.getName());

    private final List<StopConditionEvaluator> evaluators;
    private int iteration;
    private int tokenUsage;
    private boolean aborted;
    private long startNanoTime;
    private String stopReason;
    private Map<String, Object> lastResult;

    public LoopCoordinator() {
        this(List.of());
    }

    public LoopCoordinator(List<StopConditionEvaluator> evaluators) {
        this.evaluators = evaluators == null ? new ArrayList<>() : new ArrayList<>(evaluators);
    }

    public int getCurrentIteration() {
        return iteration;
    }

    public boolean isAborted() {
        return aborted;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void reset() {
        iteration = 0;
        tokenUsage = 0;
        aborted = false;
        startNanoTime = System.nanoTime();
        stopReason = null;
        lastResult = null;
        for (StopConditionEvaluator evaluator : evaluators) {
            evaluator.reset();
        }
    }

    public void incrementIteration() {
        iteration += 1;
    }

    public void addTokenUsage(int tokens) {
        if (tokens > 0) {
            tokenUsage += tokens;
        }
    }

    public void setLastResult(Map<String, Object> result) {
        lastResult = result == null ? null : new LinkedHashMap<>(result);
    }

    public void requestAbort() {
        aborted = true;
    }

    public boolean shouldContinue() {
        if (aborted) {
            stopReason = "Aborted";
            return false;
        }
        StopEvaluationContext context = buildEvalContext();
        for (StopConditionEvaluator evaluator : evaluators) {
            try {
                if (evaluator.shouldStop(context)) {
                    stopReason = evaluator.getName();
                    LOGGER.log(Level.INFO, "Stop condition met: {0}", stopReason);
                    return false;
                }
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Evaluator " + evaluator.getName() + " raised an error", ex);
            }
        }
        return true;
    }

    public CompletionPromiseEvaluator getCompletionPromiseEvaluator() {
        for (StopConditionEvaluator evaluator : evaluators) {
            if (evaluator instanceof CompletionPromiseEvaluator completionPromiseEvaluator) {
                return completionPromiseEvaluator;
            }
        }
        return null;
    }

    public Map<String, Object> getState() {
        Map<String, Object> evaluatorStates = new LinkedHashMap<>();
        for (StopConditionEvaluator evaluator : evaluators) {
            Map<String, Object> state = evaluator.getState();
            if (state != null) {
                evaluatorStates.put(evaluator.getName(), state);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("iteration", iteration);
        state.put("token_usage", tokenUsage);
        state.put("stop_reason", stopReason);
        state.put("evaluator_states", evaluatorStates);
        return state;
    }

    public void loadState(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        iteration = intValue(data.get("iteration"), 0);
        tokenUsage = intValue(data.get("token_usage"), 0);
        stopReason = data.get("stop_reason") == null ? null : String.valueOf(data.get("stop_reason"));
        startNanoTime = System.nanoTime();
        Map<String, Object> evaluatorStates = castMap(data.get("evaluator_states"));
        for (StopConditionEvaluator evaluator : evaluators) {
            if (evaluatorStates != null && evaluatorStates.containsKey(evaluator.getName())) {
                evaluator.loadState(castMap(evaluatorStates.get(evaluator.getName())));
            }
        }
    }

    private StopEvaluationContext buildEvalContext() {
        double elapsedSeconds = startNanoTime == 0L ? 0.0 : (System.nanoTime() - startNanoTime) / 1_000_000_000.0;
        return new StopEvaluationContext(
                iteration,
                tokenUsage,
                elapsedSeconds,
                lastResult == null ? null : Map.copyOf(lastResult),
                Map.of()
        );
    }

    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
