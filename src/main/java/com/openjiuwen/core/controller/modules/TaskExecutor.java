// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;

import java.util.Iterator;

/**
 * Task executor abstract base class.
 *
 * <p>Defines the task execution interface. Different task types need to implement
 * different TaskExecutors.
 *
 * <p>Main responsibilities:
 * <ul>
 *   <li>Execute tasks ({@link #executeAbility})</li>
 *   <li>Check whether a task can be paused ({@link #canPause})</li>
 *   <li>Pause tasks ({@link #pause})</li>
 *   <li>Check whether a task can be canceled ({@link #canCancel})</li>
 *   <li>Cancel tasks ({@link #cancel})</li>
 * </ul>
 *
 * <p>Python reference: {@code modules/task_scheduler.py::TaskExecutor}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class TaskExecutor {

    /** Controller configuration. */
    protected final ControllerConfig config;

    /** Ability manager. */
    protected final AbilityManager abilityManager;

    /** Context engine. */
    protected final ContextEngine contextEngine;

    /** Task manager. */
    protected final TaskManager taskManager;

    /** Event queue. */
    protected final EventQueue eventQueue;

    /**
     * Constructs a TaskExecutor with the given dependencies.
     *
     * @param dependencies the task executor dependencies
     */
    protected TaskExecutor(TaskExecutorDependencies dependencies) {
        this.config = dependencies.getConfig();
        this.abilityManager = dependencies.getAbilityManager();
        this.contextEngine = dependencies.getContextEngine();
        this.taskManager = dependencies.getTaskManager();
        this.eventQueue = dependencies.getEventQueue();
    }

    /**
     * Execute task.
     *
     * @param taskId  the task ID
     * @param session the session object
     * @return an iterator over output chunks generated during task execution
     */
    public abstract Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session);

    /**
     * Check whether the task can be paused.
     *
     * @param taskId  the task ID
     * @param session the session object
     * @return a result pair: [canPause, reason]; reason is empty if canPause is true
     */
    public abstract PauseResult canPause(String taskId, Session session);

    /**
     * Pause task.
     *
     * @param taskId  the task ID
     * @param session the session object
     * @return true if the task was successfully paused
     */
    public abstract boolean pause(String taskId, Session session);

    /**
     * Check whether the task can be canceled.
     *
     * @param taskId  the task ID
     * @param session the session object
     * @return a result pair: [canCancel, reason]; reason is empty if canCancel is true
     */
    public abstract CancelResult canCancel(String taskId, Session session);

    /**
     * Cancel task.
     *
     * @param taskId  the task ID
     * @param session the session object
     * @return true if the task was successfully canceled
     */
    public abstract boolean cancel(String taskId, Session session);

    /**
     * Result of a "can pause" check.
     *
     * @param canPause whether the task can be paused
     * @param reason   the reason if it cannot be paused (empty string if it can)
     */
    public record PauseResult(boolean canPause, String reason) {}

    /**
     * Result of a "can cancel" check.
     *
     * @param canCancel whether the task can be canceled
     * @param reason    the reason if it cannot be canceled (empty string if it can)
     */
    public record CancelResult(boolean canCancel, String reason) {}
}

