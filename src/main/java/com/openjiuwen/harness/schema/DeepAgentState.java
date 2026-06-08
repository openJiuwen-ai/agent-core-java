/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.harness.schema.task.TaskPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-invoke mutable DeepAgent state.
 *
 * <p>Mirrors Python's {@code DeepAgentState} in
 * {@code openjiuwen/harness/schema/state.py}.</p>
 */
public final class DeepAgentState {

    public static final String SESSION_STATE_KEY = "deepagent";
    public static final String SESSION_RUNTIME_ATTR = "_deepagent_runtime_state";

    private int iteration;
    private TaskPlan taskPlan;
    private Map<String, Object> stopConditionState;
    private final List<String> pendingFollowUps;
    private PlanModeState planMode;

    public DeepAgentState() {
        this(0, null, null, List.of(), new PlanModeState());
    }

    public DeepAgentState(
            int iteration,
            TaskPlan taskPlan,
            Map<String, Object> stopConditionState,
            List<String> pendingFollowUps,
            PlanModeState planMode
    ) {
        this.iteration = iteration;
        this.taskPlan = taskPlan;
        this.stopConditionState = stopConditionState == null ? null : new LinkedHashMap<>(stopConditionState);
        this.pendingFollowUps = pendingFollowUps == null ? new ArrayList<>() : new ArrayList<>(pendingFollowUps);
        this.planMode = planMode == null ? new PlanModeState() : planMode;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public TaskPlan getTaskPlan() {
        return taskPlan;
    }

    public void setTaskPlan(TaskPlan taskPlan) {
        this.taskPlan = taskPlan;
    }

    public Map<String, Object> getStopConditionState() {
        return stopConditionState == null ? null : new LinkedHashMap<>(stopConditionState);
    }

    public void setStopConditionState(Map<String, Object> stopConditionState) {
        this.stopConditionState = stopConditionState == null ? null : new LinkedHashMap<>(stopConditionState);
    }

    public List<String> getPendingFollowUps() {
        return new ArrayList<>(pendingFollowUps);
    }

    public PlanModeState getPlanMode() {
        return planMode;
    }

    public void setPlanMode(PlanModeState planMode) {
        this.planMode = planMode == null ? new PlanModeState() : planMode;
    }

    public Map<String, Object> toSessionMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("iteration", iteration);
        map.put("task_plan", taskPlan == null ? null : taskPlan.toMap());
        map.put("stop_condition_state", stopConditionState == null ? null : new LinkedHashMap<>(stopConditionState));
        map.put("pending_follow_ups", new ArrayList<>(pendingFollowUps));
        map.put("plan_mode", planMode.toMap());
        return map;
    }

    public static DeepAgentState fromSessionMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new DeepAgentState();
        }
        TaskPlan plan = null;
        Object rawPlan = data.get("task_plan");
        if (rawPlan instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            plan = TaskPlan.fromMap(normalized);
        }
        Map<String, Object> stopState = null;
        Object rawStopState = data.get("stop_condition_state");
        if (rawStopState instanceof Map<?, ?> map) {
            stopState = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                stopState.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        List<String> followUps = new ArrayList<>();
        Object rawFollowUps = data.get("pending_follow_ups");
        if (rawFollowUps instanceof Iterable<?> values) {
            for (Object value : values) {
                followUps.add(String.valueOf(value));
            }
        }
        PlanModeState modeState = PlanModeState.fromMap(castMap(data.get("plan_mode")));
        return new DeepAgentState(
                intValue(data.get("iteration"), 0),
                plan,
                stopState,
                followUps,
                modeState
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
