/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.List;

/**
 * Encapsulates all data related to task interruption.
 * <p>
 * Groups related parameters that describe the complete state when a task
 * is interrupted, making the API cleaner and more maintainable.
 * <p>
 * Mirrors Python's {@code TaskInterruptionState} dataclass.
 * 
 * @since 0.1.7
 */
public class TaskInterruptionState {
    private final Task task;
    private final AgentSessionApi session;
    private final AssistantMessage aiMessage;
    private final List<Task> remainingTasks;
    private List<Object> interactionData;
    private Integer currentIteration;

    /**
     * TaskInterruptionState.
     * 
     * @param task task
     * @param session session
     * @param aiMessage aiMessage
     * @param remainingTasks remainingTasks
     * @since 0.1.7
     */
    public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage,
            List<Task> remainingTasks) {
        this.task = task;
        this.session = session;
        this.aiMessage = aiMessage;
        this.remainingTasks = remainingTasks;
    }

    /**
     * TaskInterruptionState.
     * 
     * @param task task
     * @param session session
     * @param aiMessage aiMessage
     * @param remainingTasks remainingTasks
     * @param interactionData interactionData
     * @param currentIteration currentIteration
     * @since 0.1.7
     */
    public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage,
            List<Task> remainingTasks, List<Object> interactionData, Integer currentIteration) {
        this.task = task;
        this.session = session;
        this.aiMessage = aiMessage;
        this.remainingTasks = remainingTasks;
        this.interactionData = interactionData;
        this.currentIteration = currentIteration;
    }

    /**
     * getTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Task getTask() {
        return task;
    }

    /**
     * getSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentSessionApi getSession() {
        return session;
    }

    /**
     * getAiMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AssistantMessage getAiMessage() {
        return aiMessage;
    }

    /**
     * getRemainingTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Task> getRemainingTasks() {
        return remainingTasks;
    }

    /**
     * getInteractionData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getInteractionData() {
        return interactionData;
    }

    /**
     * setInteractionData.
     * 
     * @param interactionData interactionData
     * @since 0.1.7
     */
    public void setInteractionData(List<Object> interactionData) {
        this.interactionData = interactionData;
    }

    /**
     * getCurrentIteration.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getCurrentIteration() {
        return currentIteration;
    }

    /**
     * setCurrentIteration.
     * 
     * @param currentIteration currentIteration
     * @since 0.1.7
     */
    public void setCurrentIteration(Integer currentIteration) {
        this.currentIteration = currentIteration;
    }
}
