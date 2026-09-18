/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.legacy.config.PlannerConfig;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default Planner implementation - Plans and decomposes complex tasks.
 * Mirrors Python's concrete {@code Planner} class.
 */
public class DefaultPlanner extends Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPlanner.class);

    /**
     * Auto-generated for codecheck compliance.
     */
    public DefaultPlanner(PlannerConfig config, ContextEngine contextEngine, Object session) {
        super(config, contextEngine, session);
    }

    /**
     * Process message, plan tasks and generate task list.
     * Mirrors Python's {@code Planner.process_message()}.
     */
    @Override
    public CompletionStage<List<Task>> processMessage(Event event) {
        LOG.debug("Processing message {} with Planner", event.getEventId());
        List<Task> tasks = new ArrayList<>();
        tasks.add(createDefaultTask(event));
        return CompletableFuture.completedFuture(tasks);
    }

    private static Task createDefaultTask(Event event) {
        String desc = "Planner task for message: "
                + (event.getContent() != null ? event.getContent().getQueryText() : "No content");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("original_message_id", event.getEventId());
        meta.put("task_source", "planner");
        Task task = new Task();
        task.setTaskType(TaskType.UNDEFINED);
        task.setDescription(desc);
        task.setStatus(TaskStatus.PENDING);
        task.setMetadata(meta);
        return task;
    }
}
