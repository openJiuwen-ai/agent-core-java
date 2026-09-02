/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.Iterator;

/**
 * Abstract base class for task executors.
 * <p>
 * Defines the interface for executing tasks. Different logical task types
 * should implement their own specialized {@code TaskExecutor} subclasses.
 * <p>
 * Mirrors Python's {@code TaskExecutor(ABC)}.
 * 
 * @since 0.1.7
 */
public abstract class TaskExecutor {
    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final ControllerConfig config;

    /**
     * abilityManager.
     * 
     * @since 0.1.7
     */
    protected final Object abilityManager;

    /**
     * contextEngine.
     * 
     * @since 0.1.7
     */
    protected final ContextEngine contextEngine;

    /**
     * taskManager.
     * 
     * @since 0.1.7
     */
    protected final TaskManager taskManager;

    /**
     * eventQueue.
     * 
     * @since 0.1.7
     */
    protected final EventQueue eventQueue;

    /**
     * TaskExecutor.
     * 
     * @param dependencies dependencies
     * @since 0.1.7
     */
    public TaskExecutor(TaskExecutorDependencies dependencies) {
        this.config = dependencies.getConfig();
        this.abilityManager = dependencies.getAbilityManager();
        this.contextEngine = dependencies.getContextEngine();
        this.taskManager = dependencies.getTaskManager();
        this.eventQueue = dependencies.getEventQueue();
    }

    /**
     * Execute a task and return output chunks.
     * 
     * @param taskId task identifier
     * @param session session object
     * @return iterator of output chunks produced during execution
     * @since 0.1.7
     */
    public abstract Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session);

    /**
     * Check whether the task can be paused.
     * 
     * @param taskId task identifier
     * @param session session object
     * @return a two-element result: [canPause, reasonIfNot]
     * @since 0.1.7
     */
    public abstract PauseCheckResult canPause(String taskId, AgentSessionApi session);

    /**
     * Pause the given task.
     * 
     * @param taskId task identifier
     * @param session session object
     * @return whether the pause operation succeeded
     * @since 0.1.7
     */
    public abstract boolean pause(String taskId, AgentSessionApi session);

    /**
     * Check whether the task can be canceled.
     * 
     * @param taskId task identifier
     * @param session session object
     * @return a two-element result: [canCancel, reasonIfNot]
     * @since 0.1.7
     */
    public abstract CancelCheckResult canCancel(String taskId, AgentSessionApi session);

    /**
     * Cancel the given task.
     * 
     * @param taskId task identifier
     * @param session session object
     * @return whether the cancel operation succeeded
     * @since 0.1.7
     */
    public abstract boolean cancel(String taskId, AgentSessionApi session);

    /**
     * Result of a pause-ability check.
     * 
     * @since 0.1.7
     */
    public record PauseCheckResult(boolean canPause, String reason) {
    }

    /**
     * Result of a cancel-ability check.
     * 
     * @since 0.1.7
     */
    public record CancelCheckResult(boolean canCancel, String reason) {
    }
}
