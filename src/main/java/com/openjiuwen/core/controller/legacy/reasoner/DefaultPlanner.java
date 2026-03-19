/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default Planner implementation - Plans and decomposes complex tasks.
 * Mirrors Python's concrete {@code Planner} class.
 */
public class DefaultPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPlanner.class);

    private final Object config;
    private final Object contextEngine;
    private final Session session;

    public DefaultPlanner(Object config, Object contextEngine, Session session) {
        this.config = config;
        this.contextEngine = contextEngine;
        this.session = session;
    }

    @Override
    public Task plan(IntentDetectionController.Intent intent, Session session) {
        return createDefaultTask(intent);
    }

    /**
     * Process message, plan tasks and generate task list.
     * Mirrors Python's {@code Planner.process_message()}.
     */
    public List<Task> processMessage(Event event) {
        LOG.debug("Processing message {} with Planner", event.getEventId());
        return Collections.singletonList(createDefaultTask(event));
    }

    private static Task createDefaultTask(Event event) {
        String desc = "Planner task for message: "
                + (event.getContent() != null ? event.getContent().getQueryText() : "No content");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("original_message_id", event.getEventId());
        meta.put("task_source", "planner");
        return Task.builder()
                .taskType(TaskType.UNDEFINED)
                .description(desc)
                .status(Task.TaskStatus.PENDING)
                .metadata(meta)
                .build();
    }

    private static Task createDefaultTask(IntentDetectionController.Intent intent) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("task_source", "planner");
        return Task.builder()
                .taskType(TaskType.UNDEFINED)
                .description("Planner task from intent")
                .status(Task.TaskStatus.PENDING)
                .metadata(meta)
                .build();
    }
}
