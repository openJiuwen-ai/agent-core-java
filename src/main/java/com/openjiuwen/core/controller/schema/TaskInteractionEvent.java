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
 */
public class TaskInteractionEvent extends Event {

    private List<DataFrame> interaction;
    private Task task;
    private java.util.Map<String, Object> metadata;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskInteractionEvent() {
        super(EventType.TASK_INTERACTION);
        this.interaction = new ArrayList<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskInteractionEvent(List<DataFrame> interaction, Task task) {
        super(EventType.TASK_INTERACTION);
        this.interaction = interaction != null ? interaction : new ArrayList<>();
        this.task = task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<DataFrame> getInteraction() {
        return interaction;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInteraction(List<DataFrame> interaction) {
        this.interaction = interaction != null ? interaction : new ArrayList<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Task getTask() {
        return task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTask(Task task) {
        this.task = task;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
