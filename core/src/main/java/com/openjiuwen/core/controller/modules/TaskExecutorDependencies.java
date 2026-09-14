/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.singleagent.AbilityManager;

/**
 * Task executor dependencies.
 * <p>
 * Encapsulates all dependencies required to construct a {@link TaskExecutor} instance.
 * Reduces parameter passing complexity and improves maintainability.
 * <p>
 * Mirrors Python's {@code TaskExecutor.__init__} dependency parameters in
 * {@code openjiuwen/core/controller/modules/task_executor.py}.
 */
public class TaskExecutorDependencies {

    private final ControllerConfig config;
    private final AbilityManager abilityManager;
    private final ContextEngine contextEngine;
    private final TaskManager taskManager;
    private final EventQueue eventQueue;

    public TaskExecutorDependencies(
            ControllerConfig config,
            AbilityManager abilityManager,
            ContextEngine contextEngine,
            TaskManager taskManager,
            EventQueue eventQueue
    ) {
        this.config = config;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.taskManager = taskManager;
        this.eventQueue = eventQueue;
    }

    public ControllerConfig getConfig() {
        return config;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public EventQueue getEventQueue() {
        return eventQueue;
    }
}

