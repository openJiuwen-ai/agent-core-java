/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context_engine.ContextEngine;
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
 * Mirrors Python's {@code EventHandler} in
 * {@code openjiuwen/core/controller/modules/event_handler.py}.
 */
public abstract class EventHandler {

    protected ControllerConfig config;
    protected ContextEngine contextEngine;
    protected Object abilityManager;
    protected TaskManager taskManager;
    protected TaskScheduler taskScheduler;

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

    public Object getAbilityManager() {
        return abilityManager;
    }

    public void setAbilityManager(Object abilityManager) {
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

    /**
     * Handle follow-up events.
     * <p>
     * Python returns a non-empty acknowledgement by default so message queues do
     * not treat the response as empty.
     *
     * @param inputs event handler input containing event and session information
     * @return default unsupported status acknowledgement
     */
    public Map<String, Object> handleFollowUp(EventHandlerInput inputs) {
        return Map.of("status", "not_supported");
    }

    /**
     * Prepare a new execution round.
     *
     * @return round identifier for correlation
     */
    public int prepareRound() {
        return 0;
    }

    /**
     * Wait for round completion.
     *
     * @return result data
     */
    public Map<String, Object> waitCompletion() {
        return waitCompletion(null);
    }

    /**
     * Wait for round completion.
     *
     * @param timeout max seconds to wait, or null
     * @return result data
     */
    public Map<String, Object> waitCompletion(Double timeout) {
        return Map.of("status", "completed");
    }

    /**
     * Called when the session is being aborted.
     */
    public void onAbort() {
    }
}

