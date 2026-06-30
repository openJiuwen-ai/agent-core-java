/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;

/**
 * Task executor dependencies.
 * <p>
 * Encapsulates all dependencies required to construct a {@link TaskExecutor} instance.
 * Reduces parameter passing complexity and improves maintainability.
 * <p>
 * Mirrors Python's {@code TaskExecutorDependencies} dataclass.
 */
public class TaskExecutorDependencies {

    private final ControllerConfig config;
    private final Object abilityManager;
    private final ContextEngine contextEngine;
    private final TaskManager taskManager;
    private final EventQueue eventQueue;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskExecutorDependencies(
            ControllerConfig config,
            Object abilityManager,
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getAbilityManager() {
        return abilityManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EventQueue getEventQueue() {
        return eventQueue;
    }
}
