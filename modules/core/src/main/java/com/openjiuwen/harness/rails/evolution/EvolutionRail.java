/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class EvolutionRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class EvolutionRail extends DeepAgentRail {
    private final EvolutionTriggerPoint evolutionTrigger;
    private final boolean isAccumulateTrajectory;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<String> toolTrace = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Object> pendingApprovalEvents = new ArrayList<>();
    private boolean isEvolutionRunning;

    /**
     * EvolutionRail.
     * 
     * @since 0.1.7
     */
    public EvolutionRail() {
        this(EvolutionTriggerPoint.AFTER_INVOKE, false);
    }

    /**
     * EvolutionRail.
     * 
     * @param evolutionTrigger evolutionTrigger
     * @param isAccumulateTrajectory isAccumulateTrajectory
     * @since 0.1.7
     */
    public EvolutionRail(EvolutionTriggerPoint evolutionTrigger, boolean isAccumulateTrajectory) {
        this.evolutionTrigger = evolutionTrigger != null ? evolutionTrigger : EvolutionTriggerPoint.AFTER_INVOKE;
        this.isAccumulateTrajectory = isAccumulateTrajectory;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 60;
    }

    /**
     * beforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (!isAccumulateTrajectory) {
            toolTrace.clear();
        }
        onBeforeInvoke(ctx);
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx != null && ctx.getInputs() instanceof ToolCallInputs inputs && inputs.getToolName() != null) {
            toolTrace.add(inputs.getToolName());
        }
        onAfterToolCall(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_TOOL_CALL) {
            runEvolution(ctx);
        }
    }

    /**
     * afterInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        onAfterInvoke(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_INVOKE) {
            runEvolution(ctx);
        }
    }

    /**
     * onBeforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    protected void onBeforeInvoke(AgentCallbackContext ctx) {
    }

    /**
     * onAfterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    protected void onAfterToolCall(AgentCallbackContext ctx) {
    }

    /**
     * onAfterInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    protected void onAfterInvoke(AgentCallbackContext ctx) {
    }

    /**
     * runEvolution.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    protected void runEvolution(AgentCallbackContext ctx) {
        isEvolutionRunning = false;
    }

    /**
     * emitApprovalEvent.
     * 
     * @param event event
     * @since 0.1.7
     */
    protected void emitApprovalEvent(Object event) {
        if (event != null) {
            pendingApprovalEvents.add(event);
        }
    }

    /**
     * markEvolutionRunning.
     * 
     * @param isRunning isRunning
     * @since 0.1.7
     */
    protected void markEvolutionRunning(boolean isRunning) {
        isEvolutionRunning = isRunning;
    }

    /**
     * toolTrace.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> toolTrace() {
        return List.copyOf(toolTrace);
    }

    /**
     * recordToolCall.
     * 
     * @param toolName toolName
     * @since 0.1.7
     */
    public void recordToolCall(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            toolTrace.add(toolName);
        }
    }

    /**
     * getEvolutionTrigger.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EvolutionTriggerPoint getEvolutionTrigger() {
        return evolutionTrigger;
    }

    /**
     * isAccumulateTrajectory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAccumulateTrajectory() {
        return isAccumulateTrajectory;
    }

    /**
     * isEvolutionRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEvolutionRunning() {
        return isEvolutionRunning;
    }

    /**
     * drainPendingApprovalEvents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> drainPendingApprovalEvents() {
        List<Object> drained = new ArrayList<>(pendingApprovalEvents);
        pendingApprovalEvents.clear();
        return drained;
    }
}
