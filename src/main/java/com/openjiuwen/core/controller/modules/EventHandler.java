// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.singleagent.AbilityManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for event handlers.
 *
 * <p>Defines the event handling interface. Different types of controllers need
 * to implement different event handlers.
 *
 * <p>Main responsibilities:
 * <ul>
 *   <li>handle input events (handleInput)</li>
 *   <li>handle task interaction events (handleTaskInteraction)</li>
 *   <li>handle task completion events (handleTaskCompletion)</li>
 *   <li>handle task failure events (handleTaskFailed)</li>
 * </ul>
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class EventHandler {

    private ControllerConfig config;
    private ContextEngine contextEngine;
    private AbilityManager abilityManager;
    private TaskManager taskManager;
    private TaskScheduler taskScheduler;

    /**
     * Default constructor. All dependencies are initialized to null
     * and must be injected via property setters.
     */
    protected EventHandler() {
    }

    // Getters and Setters for dependency injection

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = contextEngine;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public void setAbilityManager(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Handle input events.
     *
     * @param inputs event handler input containing event and session information
     * @return future containing response data (nullable)
     */
    public abstract CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs);

    /**
     * Handle task interaction events.
     *
     * @param inputs event handler input containing event and session information
     * @return future containing response data (nullable)
     */
    public abstract CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs);

    /**
     * Handle task completion events.
     *
     * @param inputs event handler input containing event and session information
     * @return future containing response data (nullable)
     */
    public abstract CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs);

    /**
     * Handle task failure events.
     *
     * @param inputs event handler input containing event and session information
     * @return future containing response data (nullable)
     */
    public abstract CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs);
}

