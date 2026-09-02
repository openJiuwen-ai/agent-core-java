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
 * <li>Handle input events ({@link #handleInput})</li>
 * <li>Handle task interaction events ({@link #handleTaskInteraction})</li>
 * <li>Handle task completion events ({@link #handleTaskCompletion})</li>
 * <li>Handle task failure events ({@link #handleTaskFailed})</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code EventHandler(ABC)}.
 * 
 * @since 0.1.7
 */
public abstract class EventHandler {
    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected ControllerConfig config;

    /**
     * contextEngine.
     * 
     * @since 0.1.7
     */
    protected ContextEngine contextEngine;

    /**
     * abilityManager.
     * 
     * @since 0.1.7
     */
    protected Object abilityManager;

    /**
     * taskManager.
     * 
     * @since 0.1.7
     */
    protected TaskManager taskManager;

    /**
     * taskScheduler.
     * 
     * @since 0.1.7
     */
    protected TaskScheduler taskScheduler;

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
     * setConfig.
     * 
     * @param config config
     * @since 0.1.7
     */
    public void setConfig(ControllerConfig config) {
        this.config = config;
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
     * setContextEngine.
     * 
     * @param contextEngine contextEngine
     * @since 0.1.7
     */
    public void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = contextEngine;
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
     * setAbilityManager.
     * 
     * @param abilityManager abilityManager
     * @since 0.1.7
     */
    public void setAbilityManager(Object abilityManager) {
        this.abilityManager = abilityManager;
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
     * setTaskManager.
     * 
     * @param taskManager taskManager
     * @since 0.1.7
     */
    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    /**
     * getTaskScheduler.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    /**
     * setTaskScheduler.
     * 
     * @param taskScheduler taskScheduler
     * @since 0.1.7
     */
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Handle input events.
     * 
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     * @since 0.1.7
     */
    public abstract Map<String, Object> handleInput(EventHandlerInput inputs);

    /**
     * Handle task interaction events.
     * 
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     * @since 0.1.7
     */
    public abstract Map<String, Object> handleTaskInteraction(EventHandlerInput inputs);

    /**
     * Handle task completion events.
     * 
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     * @since 0.1.7
     */
    public abstract Map<String, Object> handleTaskCompletion(EventHandlerInput inputs);

    /**
     * Handle task failure events.
     * 
     * @param inputs event handler input containing event and session information
     * @return response data, or null
     * @since 0.1.7
     */
    public abstract Map<String, Object> handleTaskFailed(EventHandlerInput inputs);
}
