/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.context_engine.ContextEngine;
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
 * Planner - Plans and decomposes complex tasks.
 *
 * <p>Mirrors Python's {@code Planner} in
 * {@code openjiuwen/core/controller/legacy/reasoner/planner.py}.</p>
 */
public class Planner {

    private static final Logger LOG = LoggerFactory.getLogger(Planner.class);

    private final PlannerConfig config;
    private final ContextEngine contextEngine;
    private final Object session;

    public Planner(PlannerConfig config, ContextEngine contextEngine, Object session) {
        this.config = config;
        this.contextEngine = contextEngine;
        this.session = session;
    }

    public PlannerConfig getConfig() {
        return config;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public Object getSession() {
        return session;
    }

    /**
     * Process message, plan tasks and generate task list.
     *
     * @param event input event
     * @return generated task list wrapped in a completed stage
     */
    public CompletionStage<List<Task>> processMessage(Event event) {
        LOG.debug("Processing message {} with Planner", event.getEventId());
        List<Task> tasks = new ArrayList<>();
        tasks.add(createDefaultTask(event));
        return CompletableFuture.completedFuture(tasks);
    }

    private static Task createDefaultTask(Event event) {
        String query = event.getContent() == null ? "No content" : event.getContent().getQueryText();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("original_message_id", event.getEventId());
        metadata.put("task_source", "planner");

        Task task = new Task();
        task.setTaskType(TaskType.UNDEFINED);
        task.setDescription("Planner task for message: " + query);
        task.setStatus(TaskStatus.PENDING);
        task.setMetadata(metadata);
        return task;
    }
}
