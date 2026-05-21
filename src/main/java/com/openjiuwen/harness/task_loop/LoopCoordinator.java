/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.StopEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LoopCoordinator — controls the DeepAgent outer task loop.
 *
 * <p>Tracks round count, token usage, wall-clock time, and abort
 * flag. {@code shouldContinue()} evaluates a chain of
 * StopConditionEvaluator objects with OR semantics.
 *
 * <p>Mirrors Python's {@code LoopCoordinator} in
 * {@code openjiuwen.harness.task_loop.loop_coordinator}.
 */
public class LoopCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(LoopCoordinator.class);

    private final List<StopConditionEvaluator> evaluators;
    private final AtomicInteger iteration = new AtomicInteger(0);
    private final AtomicInteger tokenUsage = new AtomicInteger(0);
    private final AtomicBoolean aborted = new AtomicBoolean(false);
    private final AtomicReference<Double> startTime = new AtomicReference<>(0.0);
    private final AtomicReference<String> stopReason = new AtomicReference<>(null);
    private final AtomicReference<Map<String, Object>> lastResult = new AtomicReference<>(null);

    /**
     * Construct with evaluators list.
     */
    public LoopCoordinator(List<StopConditionEvaluator> evaluators) {
        this.evaluators = evaluators != null ? new ArrayList<>(evaluators) : new ArrayList<>();
    }

    /**
     * Default constructor.
     */
    public LoopCoordinator() {
        this(null);
    }

    // -- read-only properties --

    /**
     * Number of completed rounds.
     */
    public int getCurrentIteration() {
        return iteration.get();
    }

    /**
     * Whether abort has been requested.
     */
    public boolean isAborted() {
        return aborted.get();
    }

    /**
     * Name of the evaluator that stopped the loop.
     */
    public String getStopReason() {
        return stopReason.get();
    }

    /**
     * Total token usage so far.
     */
    public int getTokenUsage() {
        return tokenUsage.get();
    }

    /**
     * Elapsed time in seconds since start.
     */
    public double getElapsedSeconds() {
        double start = startTime.get();
        if (start <= 0) {
            return 0.0;
        }
        return (System.nanoTime() / 1_000_000_000.0) - start;
    }

    // -- mutation --

    /**
     * Reset for a new invoke cycle.
     */
    public void reset() {
        iteration.set(0);
        tokenUsage.set(0);
        aborted.set(false);
        startTime.set(System.nanoTime() / 1_000_000_000.0);
        stopReason.set(null);
        lastResult.set(null);
        for (StopConditionEvaluator ev : evaluators) {
            ev.reset();
        }
    }

    /**
     * Record one completed round.
     */
    public void incrementIteration() {
        iteration.incrementAndGet();
    }

    /**
     * Accumulate token consumption.
     */
    public void addTokenUsage(int tokens) {
        if (tokens > 0) {
            tokenUsage.addAndGet(tokens);
        }
    }

    /**
     * Store the most recent round result.
     */
    public void setLastResult(Map<String, Object> result) {
        lastResult.set(result);
    }

    /**
     * Signal the loop to stop immediately.
     */
    public void requestAbort() {
        aborted.set(true);
    }

    // -- stop evaluation --

    /**
     * Return true if the loop may proceed.
     *
     * <p>Evaluates all evaluators with OR semantics — the first
     * evaluator that returns true from shouldStop()
     * terminates the loop and records the stop reason.
     */
    public boolean shouldContinue() {
        if (aborted.get()) {
            stopReason.set("Aborted");
            return false;
        }

        StopEvaluationContext ctx = buildEvalContext();
        for (StopConditionEvaluator ev : evaluators) {
            try {
                if (ev.shouldStop(ctx)) {
                    stopReason.set(ev.getName());
                    LOG.info("Stop condition met: {}", ev.getName());
                    return false;
                }
            } catch (Exception e) {
                LOG.warn("Evaluator {} raised an error", ev.getName(), e);
            }
        }
        return true;
    }

    /**
     * Build evaluation context from current state.
     */
    private StopEvaluationContext buildEvalContext() {
        return StopEvaluationContext.builder()
                .iteration(iteration.get())
                .tokenUsage(tokenUsage.get())
                .elapsedSeconds(getElapsedSeconds())
                .lastResult(lastResult.get())
                .extra(new HashMap<>())
                .build();
    }

    // -- state persistence --

    /**
     * Export a JSON-safe snapshot for checkpointing.
     */
    public Map<String, Object> getState() {
        Map<String, Object> evStates = new LinkedHashMap<>();
        for (StopConditionEvaluator ev : evaluators) {
            Map<String, Object> s = ev.getState();
            if (s != null) {
                evStates.put(ev.getName(), s);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("iteration", iteration.get());
        state.put("token_usage", tokenUsage.get());
        state.put("stop_reason", stopReason.get());
        state.put("evaluator_states", evStates);
        return state;
    }

    /**
     * Restore state from a persisted snapshot.
     */
    public void loadState(Map<String, Object> data) {
        if (data == null) return;
        Object iterObj = data.get("iteration");
        if (iterObj instanceof Number) {
            iteration.set(((Number) iterObj).intValue());
        }
        Object tokensObj = data.get("token_usage");
        if (tokensObj instanceof Number) {
            tokenUsage.set(((Number) tokensObj).intValue());
        }
        Object reasonObj = data.get("stop_reason");
        if (reasonObj instanceof String) {
            stopReason.set((String) reasonObj);
        }
        Object evStatesObj = data.get("evaluator_states");
        if (evStatesObj instanceof Map) {
            Map<String, Object> evStates = (Map<String, Object>) evStatesObj;
            for (StopConditionEvaluator ev : evaluators) {
                Object evData = evStates.get(ev.getName());
                if (evData instanceof Map) {
                    ev.loadState((Map<String, Object>) evData);
                }
            }
        }
    }

    /**
     * Add an evaluator.
     */
    public void addEvaluator(StopConditionEvaluator evaluator) {
        if (evaluator != null) {
            evaluators.add(evaluator);
        }
    }

    /**
     * Get evaluators list.
     */
    public List<StopConditionEvaluator> getEvaluators() {
        return new ArrayList<>(evaluators);
    }
}