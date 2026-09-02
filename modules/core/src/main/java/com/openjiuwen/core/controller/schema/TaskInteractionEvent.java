/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Task interaction event.
 * <p>
 * Generated when user interaction is required during task execution.
 * <p>
 * Mirrors Python's {@code TaskInteractionEvent}.
 * 
 * @since 0.1.7
 */
public class TaskInteractionEvent extends Event {
    private List<DataFrame> interaction;
    private Task task;
    private java.util.Map<String, Object> metadata;

    /**
     * TaskInteractionEvent.
     * 
     * @since 0.1.7
     */
    public TaskInteractionEvent() {
        super(EventType.TASK_INTERACTION);
        this.interaction = new ArrayList<>();
    }

    /**
     * TaskInteractionEvent.
     * 
     * @param interaction interaction
     * @param task task
     * @since 0.1.7
     */
    public TaskInteractionEvent(List<DataFrame> interaction, Task task) {
        super(EventType.TASK_INTERACTION);
        this.interaction = interaction != null ? interaction : new ArrayList<>();
        this.task = task;
    }

    /**
     * getInteraction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DataFrame> getInteraction() {
        return interaction;
    }

    /**
     * setInteraction.
     * 
     * @param interaction interaction
     * @since 0.1.7
     */
    public void setInteraction(List<DataFrame> interaction) {
        this.interaction = interaction != null ? interaction : new ArrayList<>();
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
     * setTask.
     * 
     * @param task task
     * @since 0.1.7
     */
    public void setTask(Task task) {
        this.task = task;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
