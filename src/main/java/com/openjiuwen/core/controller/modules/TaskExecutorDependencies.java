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
 * 
 * @since 0.1.7
 */
public class TaskExecutorDependencies {
    private final ControllerConfig config;
    private final Object abilityManager;
    private final ContextEngine contextEngine;
    private final TaskManager taskManager;
    private final EventQueue eventQueue;

    /**
     * TaskExecutorDependencies.
     * 
     * @param config config
     * @param abilityManager abilityManager
     * @param contextEngine contextEngine
     * @param taskManager taskManager
     * @param eventQueue eventQueue
     * @since 0.1.7
     */
    public TaskExecutorDependencies(ControllerConfig config, Object abilityManager, ContextEngine contextEngine,
            TaskManager taskManager, EventQueue eventQueue) {
        this.config = config;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.taskManager = taskManager;
        this.eventQueue = eventQueue;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * getAbilityManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getAbilityManager() {
        return abilityManager;
    }

    /**
     * getContextEngine.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * getTaskManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * getEventQueue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EventQueue getEventQueue() {
        return eventQueue;
    }
}
