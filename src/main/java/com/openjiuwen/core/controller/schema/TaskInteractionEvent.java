/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    public TaskInteractionEvent() {
        super(EventType.TASK_INTERACTION);
        this.interaction = new ArrayList<>();
    }

    public TaskInteractionEvent(List<DataFrame> interaction, Task task) {
        super(EventType.TASK_INTERACTION);
        this.interaction = interaction != null ? interaction : new ArrayList<>();
        this.task = task;
    }

    public List<DataFrame> getInteraction() {
        return interaction;
    }

    public void setInteraction(List<DataFrame> interaction) {
        this.interaction = interaction != null ? interaction : new ArrayList<>();
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
