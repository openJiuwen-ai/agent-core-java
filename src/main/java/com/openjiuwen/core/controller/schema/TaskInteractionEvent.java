// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task Interaction Event.
 *
 * <p>Event generated when user interaction is required during task execution.
 * This event is generated when a task requires the user to provide additional
 * information or confirmation.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskInteractionEvent extends Event {

    private final List<BaseDataFrame> interaction;
    private final Task task;

    /**
     * Default constructor.
     */
    public TaskInteractionEvent() {
        this(new ArrayList<>(), null);
    }

    /**
     * Constructor with interaction data and task.
     *
     * @param interaction the interaction content list
     * @param task        the associated task (can be null)
     */
    public TaskInteractionEvent(List<BaseDataFrame> interaction, Task task) {
        super(EventType.TASK_INTERACTION);
        this.interaction = interaction != null ? new ArrayList<>(interaction) : new ArrayList<>();
        this.task = task;
    }

    /**
     * Full constructor with all event fields.
     *
     * @param eventId     the event ID
     * @param metadata    the metadata
     * @param interaction the interaction content list
     * @param task        the associated task
     */
    public TaskInteractionEvent(String eventId, Map<String, Object> metadata,
                                 List<BaseDataFrame> interaction, Task task) {
        super(EventType.TASK_INTERACTION, eventId, metadata);
        this.interaction = interaction != null ? new ArrayList<>(interaction) : new ArrayList<>();
        this.task = task;
    }

    /**
     * Gets the interaction content.
     *
     * @return the interaction list
     */
    public List<BaseDataFrame> getInteraction() {
        return interaction;
    }

    /**
     * Gets the associated task.
     *
     * @return the task, or null
     */
    public Task getTask() {
        return task;
    }
}

