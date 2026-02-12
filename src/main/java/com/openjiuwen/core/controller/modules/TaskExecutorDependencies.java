// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.singleagent.AbilityManager;

/**
 * Task executor dependencies.
 *
 * <p>Encapsulates all dependencies required to construct a {@link TaskExecutor} instance.
 * This reduces parameter passing complexity and improves maintainability.
 *
 * <p>Python reference: {@code modules/task_scheduler.py::TaskExecutorDependencies}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskExecutorDependencies {

    private final ControllerConfig config;
    private final AbilityManager abilityManager;
    private final ContextEngine contextEngine;
    private final TaskManager taskManager;
    private final EventQueue eventQueue;

    /**
     * Constructs a TaskExecutorDependencies instance.
     *
     * @param config         the controller configuration
     * @param abilityManager the ability manager containing available tools, workflows, etc.
     * @param contextEngine  the context engine for managing conversation context
     * @param taskManager    the task manager for updating task status
     * @param eventQueue     the event queue for publishing task-related events
     */
    public TaskExecutorDependencies(ControllerConfig config,
                                     AbilityManager abilityManager,
                                     ContextEngine contextEngine,
                                     TaskManager taskManager,
                                     EventQueue eventQueue) {
        this.config = config;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.taskManager = taskManager;
        this.eventQueue = eventQueue;
    }

    /**
     * Gets the controller configuration.
     *
     * @return the configuration
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Gets the ability manager.
     *
     * @return the ability manager
     */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    /**
     * Gets the context engine.
     *
     * @return the context engine
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Gets the task manager.
     *
     * @return the task manager
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Gets the event queue.
     *
     * @return the event queue
     */
    public EventQueue getEventQueue() {
        return eventQueue;
    }
}

