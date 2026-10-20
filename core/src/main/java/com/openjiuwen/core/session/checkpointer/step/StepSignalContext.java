/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.Objects;

/**
 * Execution-time facts the evaluator needs to decide whether the current step
 * boundary should persist the state snapshot.
 *
 * <p>Populated per invocation by the embedding agent (ReAct loop or DeepAgent
 * task loop). Every field is optional; missing facts simply weaken the signal
 * set, never fail the evaluation.</p>
 *
 * @since 0.1.17
 */
public class StepSignalContext {
    private String taskType;
    private boolean hasWorkflow;
    private boolean hasInteractiveCapability;
    private int toolCount;
    private long completedSteps;
    private long lastStepDurationMillis;

    public StepSignalContext() {
    }

    /**
     * Create a fully populated signal context.
     *
     * @param taskType explicit task type annotation, e.g. LONG_RUNNING; empty when absent
     * @param hasWorkflow whether a workflow / multi-phase task is declared
     * @param hasInteractiveCapability whether HITL tools (ask_user / approval) are registered
     * @param toolCount number of mounted tools
     * @param completedSteps steps already completed within the current invocation
     * @param lastStepDurationMillis duration of the most recent step in millis
     */
    public StepSignalContext(String taskType, boolean hasWorkflow, boolean hasInteractiveCapability,
                             int toolCount, long completedSteps, long lastStepDurationMillis) {
        this.taskType = taskType == null ? "" : taskType;
        this.hasWorkflow = hasWorkflow;
        this.hasInteractiveCapability = hasInteractiveCapability;
        this.toolCount = toolCount;
        this.completedSteps = completedSteps;
        this.lastStepDurationMillis = lastStepDurationMillis;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType == null ? "" : taskType;
    }

    public boolean isHasWorkflow() {
        return hasWorkflow;
    }

    public void setHasWorkflow(boolean hasWorkflow) {
        this.hasWorkflow = hasWorkflow;
    }

    public boolean isHasInteractiveCapability() {
        return hasInteractiveCapability;
    }

    public void setHasInteractiveCapability(boolean hasInteractiveCapability) {
        this.hasInteractiveCapability = hasInteractiveCapability;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    public long getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(long completedSteps) {
        this.completedSteps = completedSteps;
    }

    public long getLastStepDurationMillis() {
        return lastStepDurationMillis;
    }

    public void setLastStepDurationMillis(long lastStepDurationMillis) {
        this.lastStepDurationMillis = lastStepDurationMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StepSignalContext context)) {
            return false;
        }
        return hasWorkflow == context.hasWorkflow
                && hasInteractiveCapability == context.hasInteractiveCapability
                && toolCount == context.toolCount
                && completedSteps == context.completedSteps
                && lastStepDurationMillis == context.lastStepDurationMillis
                && taskType.equals(context.taskType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskType, hasWorkflow, hasInteractiveCapability, toolCount,
                completedSteps, lastStepDurationMillis);
    }

    @Override
    public String toString() {
        return "StepSignalContext{taskType=" + taskType
                + ", hasWorkflow=" + hasWorkflow
                + ", hasInteractiveCapability=" + hasInteractiveCapability
                + ", toolCount=" + toolCount
                + ", completedSteps=" + completedSteps
                + ", lastStepDurationMillis=" + lastStepDurationMillis + "}";
    }
}
