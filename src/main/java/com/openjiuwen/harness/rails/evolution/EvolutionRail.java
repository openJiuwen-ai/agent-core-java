/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Captures trajectories and emits evolution trigger snapshots.
 *
 * <p>Mirrors Python's {@code EvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/evolution_rail.py}.</p>
 */
public class EvolutionRail extends DeepAgentRail {

    private final Integer maxTrajectorySteps;
    private final EvolutionTriggerPoint evolutionTrigger;
    private final boolean asyncEvolution;
    private final Set<String> disabledSkills = new LinkedHashSet<>();
    private final List<Map<String, Object>> trajectory = new ArrayList<>();
    private final Queue<Map<String, Object>> hostEvents = new ArrayDeque<>();

    public EvolutionRail() {
        this(100, EvolutionTriggerPoint.AFTER_INVOKE, true, Set.of());
    }

    public EvolutionRail(
            int maxTrajectorySteps,
            EvolutionTriggerPoint evolutionTrigger,
            boolean asyncEvolution,
            Set<String> disabledSkills
    ) {
        this(Integer.valueOf(maxTrajectorySteps), evolutionTrigger, asyncEvolution, disabledSkills);
    }

    public EvolutionRail(
            Integer maxTrajectorySteps,
            EvolutionTriggerPoint evolutionTrigger,
            boolean asyncEvolution,
            Set<String> disabledSkills
    ) {
        setPriority(75);
        this.maxTrajectorySteps = maxTrajectorySteps == null ? null : Math.max(1, maxTrajectorySteps);
        this.evolutionTrigger = evolutionTrigger == null ? EvolutionTriggerPoint.AFTER_INVOKE : evolutionTrigger;
        this.asyncEvolution = asyncEvolution;
        if (disabledSkills != null) {
            this.disabledSkills.addAll(disabledSkills);
        }
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        trajectory.clear();
        appendStep("before_invoke", ctx);
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        appendStep("after_model_call", ctx);
        maybeTrigger(EvolutionTriggerPoint.AFTER_MODEL_CALL, ctx);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        appendStep("after_tool_call", ctx);
        maybeTrigger(EvolutionTriggerPoint.AFTER_TOOL_CALL, ctx);
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        appendStep("after_task_iteration", ctx);
        maybeTrigger(EvolutionTriggerPoint.AFTER_TASK_ITERATION, ctx);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        appendStep("after_invoke", ctx);
        maybeTrigger(EvolutionTriggerPoint.AFTER_INVOKE, ctx);
    }

    public List<Map<String, Object>> buildTrajectory() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> step : trajectory) {
            copy.add(new LinkedHashMap<>(step));
        }
        return copy;
    }

    protected List<Map<String, Object>> mutableTrajectory() {
        return trajectory;
    }

    protected void resetTrajectoryBuilder() {
        trajectory.clear();
    }

    protected Map<String, Object> lastTrajectoryStep() {
        if (trajectory.isEmpty()) {
            return null;
        }
        return trajectory.getLast();
    }

    public Set<String> getDisabledSkills() {
        return new LinkedHashSet<>(disabledSkills);
    }

    public boolean isAsyncEvolution() {
        return asyncEvolution;
    }

    public void emitHostEvent(Map<String, Object> event) {
        if (event != null) {
            hostEvents.add(new LinkedHashMap<>(event));
        }
    }

    public List<Map<String, Object>> drainPendingHostEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        while (!hostEvents.isEmpty()) {
            events.add(hostEvents.remove());
        }
        return events;
    }

    protected boolean allowEvolutionTrigger(EvolutionTriggerPoint triggerPoint, CallbackContext ctx) {
        return evolutionTrigger == triggerPoint && evolutionTrigger != EvolutionTriggerPoint.NONE;
    }

    protected Map<String, Object> snapshotForEvolution(CallbackContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trajectory", buildTrajectory());
        snapshot.put("context", new LinkedHashMap<>(ctx.getValues()));
        snapshot.put("disabled_skills", new ArrayList<>(disabledSkills));
        return snapshot;
    }

    protected void runEvolution(Map<String, Object> snapshot) {
        emitHostEvent(Map.of("type", "evolution_snapshot", "snapshot", snapshot));
    }

    protected void appendStep(String event, CallbackContext ctx) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("event", event);
        String kind = kindForEvent(event);
        if (kind != null) {
            step.put("kind", kind);
        }
        step.put("meta", new LinkedHashMap<String, Object>());
        step.put("values", new LinkedHashMap<>(ctx.getValues()));
        trajectory.add(step);
        if (maxTrajectorySteps != null && trajectory.size() > maxTrajectorySteps) {
            trajectory.removeFirst();
        }
    }

    private String kindForEvent(String event) {
        return switch (event) {
            case "after_model_call" -> "llm";
            case "after_tool_call" -> "tool";
            case "after_task_iteration" -> "task_iteration";
            case "before_invoke", "after_invoke" -> "invoke";
            default -> null;
        };
    }

    private void maybeTrigger(EvolutionTriggerPoint triggerPoint, CallbackContext ctx) {
        if (allowEvolutionTrigger(triggerPoint, ctx)) {
            runEvolution(snapshotForEvolution(ctx));
        }
    }
}
