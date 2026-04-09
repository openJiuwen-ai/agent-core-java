/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
 */
public class TaskInterruptionState {

    private final Task task;
    private final AgentSessionApi session;
    private final AssistantMessage aiMessage;
    private final List<Task> remainingTasks;
    private List<Object> interactionData;
    private Integer currentIteration;

    public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage,
                                 List<Task> remainingTasks) {
        this.task = task;
        this.session = session;
        this.aiMessage = aiMessage;
        this.remainingTasks = remainingTasks;
    }

    public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage,
                                 List<Task> remainingTasks, List<Object> interactionData,
                                 Integer currentIteration) {
        this.task = task;
        this.session = session;
        this.aiMessage = aiMessage;
        this.remainingTasks = remainingTasks;
        this.interactionData = interactionData;
        this.currentIteration = currentIteration;
    }

    public Task getTask() {
        return task;
    }

    public AgentSessionApi getSession() {
        return session;
    }

    public AssistantMessage getAiMessage() {
        return aiMessage;
    }

    public List<Task> getRemainingTasks() {
        return remainingTasks;
    }

    public List<Object> getInteractionData() {
        return interactionData;
    }

    public void setInteractionData(List<Object> interactionData) {
        this.interactionData = interactionData;
    }

    public Integer getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(Integer currentIteration) {
        this.currentIteration = currentIteration;
    }
}
