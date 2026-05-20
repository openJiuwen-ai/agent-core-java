/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;

import java.util.Map;

/**
 * Abstract base class for event handlers.
 * <p>
 * Defines the event handling interface. Different types of controllers need
 * to implement different event handlers.
 * <p>
 * Main responsibilities:
 * <ul>
 *   <li>Handle input events ({@link #handleInput})</li>
 *   <li>Handle task interaction events ({@link #handleTaskInteraction})</li>
 *   <li>Handle task completion events ({@link #handleTaskCompletion})</li>
 *   <li>Handle task failure events ({@link #handleTaskFailed})</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code EventHandler(ABC)}.
 */
public abstract class EventHandler {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected ControllerConfig config;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected ContextEngine contextEngine;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object abilityManager;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected TaskManager taskManager;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected TaskScheduler taskScheduler;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setConfig(ControllerConfig config) {
        this.config = config;
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
    public void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = contextEngine;
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
    public void setAbilityManager(Object abilityManager) {
        this.abilityManager = abilityManager;
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
    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Handle input events.
     *
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     */
    public abstract Map<String, Object> handleInput(EventHandlerInput inputs);

    /**
     * Handle task interaction events.
     *
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     */
    public abstract Map<String, Object> handleTaskInteraction(EventHandlerInput inputs);

    /**
     * Handle task completion events.
     *
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     */
    public abstract Map<String, Object> handleTaskCompletion(EventHandlerInput inputs);

    /**
     * Handle task failure events.
     *
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     */
    public abstract Map<String, Object> handleTaskFailed(EventHandlerInput inputs);
}
