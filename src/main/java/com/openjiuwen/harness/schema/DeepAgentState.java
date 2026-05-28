/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.harness.schema.task.TaskPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-invoke mutable state.
 * <p>
 * The object lives on ctx.session while an invoke/stream request is running.
 * A serializable subset can be checkpointed to session state.
 * <p>
 * Mirrors Python's {@code DeepAgentState} in {@code openjiuwen.harness.schema.state}.
 */
public class DeepAgentState {

    /** Current iteration count. */
    private int iteration = 0;
    
    /** Current task plan. */
    private TaskPlan taskPlan = null;
    
    /** Stop condition state. */
    private Map<String, Object> stopConditionState = null;
    
    /** Pending follow-up actions. */
    private List<String> pendingFollowUps = new ArrayList<>();
    
    /** Plan mode state. */
    private PlanModeState planMode = new PlanModeState();

    public DeepAgentState() {
        // Default constructor with default values
    }

    // Getters and setters
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
        return stopConditionState;
    }

    public void setStopConditionState(Map<String, Object> stopConditionState) {
        this.stopConditionState = stopConditionState;
    }

    public List<String> getPendingFollowUps() {
        return pendingFollowUps;
    }

    public void setPendingFollowUps(List<String> pendingFollowUps) {
        this.pendingFollowUps = pendingFollowUps != null ? pendingFollowUps : new ArrayList<>();
    }

    public PlanModeState getPlanMode() {
        return planMode;
    }

    public void setPlanMode(PlanModeState planMode) {
        this.planMode = planMode != null ? planMode : new PlanModeState();
    }

    /**
     * Convert to a JSON-friendly map.
     * <p>
     * Mirrors Python's {@code to_session_dict()}.
     *
     * @return Map representation for session storage
     */
    public Map<String, Object> toSessionMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("iteration", iteration);
        map.put("task_plan", taskPlan != null ? taskPlan.toMap() : null);
        map.put("stop_condition_state", stopConditionState);
        map.put("pending_follow_ups", new ArrayList<>(pendingFollowUps));
        map.put("plan_mode", planMode.toMap());
        return map;
    }

    /**
     * Build state from session snapshot.
     * <p>
     * Mirrors Python's {@code from_session_dict()}.
     *
     * @param data Session snapshot map; null returns default state
     * @return Reconstructed DeepAgentState
     */
    public static DeepAgentState fromSessionMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new DeepAgentState();
        }
        
        DeepAgentState state = new DeepAgentState();
        state.iteration = ((Number) data.getOrDefault("iteration", 0)).intValue();
        
        Object rawPlan = data.get("task_plan");
        if (rawPlan instanceof Map) {
            state.taskPlan = TaskPlan.fromMap((Map<String, Object>) rawPlan);
        }
        
        Object rawStopCondition = data.get("stop_condition_state");
        if (rawStopCondition instanceof Map) {
            state.stopConditionState = new HashMap<>((Map<String, Object>) rawStopCondition);
        }
        
        Object rawFollowUps = data.get("pending_follow_ups");
        if (rawFollowUps instanceof List) {
            state.pendingFollowUps = new ArrayList<>((List<String>) rawFollowUps);
        }
        
        Object rawPlanMode = data.get("plan_mode");
        if (rawPlanMode instanceof Map) {
            state.planMode = PlanModeState.fromMap((Map<String, Object>) rawPlanMode);
        }
        
        return state;
    }

    @Override
    public String toString() {
        return "DeepAgentState{" +
                "iteration=" + iteration +
                ", taskPlan=" + taskPlan +
                ", stopConditionState=" + stopConditionState +
                ", pendingFollowUps=" + pendingFollowUps +
                ", planMode=" + planMode +
                '}';
    }
}