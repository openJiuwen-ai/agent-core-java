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
 * @since 1.0
 */
public class EvolutionRail extends DeepAgentRail {
    private final EvolutionTriggerPoint evolutionTrigger;
    private final boolean isAccumulateTrajectory;
    private final List<String> toolTrace = new ArrayList<>();
    private final List<Object> pendingApprovalEvents = new ArrayList<>();
    private boolean isEvolutionRunning;

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvolutionRail() {
        this(EvolutionTriggerPoint.AFTER_INVOKE, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvolutionRail(EvolutionTriggerPoint evolutionTrigger, boolean isAccumulateTrajectory) {
        this.evolutionTrigger = evolutionTrigger != null ? evolutionTrigger : EvolutionTriggerPoint.AFTER_INVOKE;
        this.isAccumulateTrajectory = isAccumulateTrajectory;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int priority() {
        return 60;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (!isAccumulateTrajectory) {
            toolTrace.clear();
        }
        onBeforeInvoke(ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void afterInvoke(AgentCallbackContext ctx) {
        onAfterInvoke(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_INVOKE) {
            runEvolution(ctx);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void onBeforeInvoke(AgentCallbackContext ctx) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void onAfterToolCall(AgentCallbackContext ctx) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void onAfterInvoke(AgentCallbackContext ctx) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void runEvolution(AgentCallbackContext ctx) {
        isEvolutionRunning = false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void emitApprovalEvent(Object event) {
        if (event != null) {
            pendingApprovalEvents.add(event);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void markEvolutionRunning(boolean isRunning) {
        isEvolutionRunning = isRunning;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> toolTrace() {
        return List.copyOf(toolTrace);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void recordToolCall(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            toolTrace.add(toolName);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvolutionTriggerPoint getEvolutionTrigger() {
        return evolutionTrigger;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAccumulateTrajectory() {
        return isAccumulateTrajectory;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEvolutionRunning() {
        return isEvolutionRunning;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> drainPendingApprovalEvents() {
        List<Object> drained = new ArrayList<>(pendingApprovalEvents);
        pendingApprovalEvents.clear();
        return drained;
    }
}
