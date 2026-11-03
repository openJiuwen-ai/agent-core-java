/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

/**
 * Standard event names for task manager lifecycle events.
 * 
 * @since 0.1.7
 */
public final class TaskManagerEvents {
    /**
     * TASK_CREATED.
     * 
     * @since 0.1.7
     */
    public static final String TASK_CREATED = "task_created";

    /**
     * TASK_RUNNING.
     * 
     * @since 0.1.7
     */
    public static final String TASK_RUNNING = "task_running";

    /**
     * TASK_COMPLETED.
     * 
     * @since 0.1.7
     */
    public static final String TASK_COMPLETED = "task_completed";

    /**
     * TASK_FAILED.
     * 
     * @since 0.1.7
     */
    public static final String TASK_FAILED = "task_failed";

    /**
     * TASK_CANCELLED.
     * 
     * @since 0.1.7
     */
    public static final String TASK_CANCELLED = "task_cancelled";

    /**
     * TASK_TIMEOUT.
     * 
     * @since 0.1.7
     */
    public static final String TASK_TIMEOUT = "task_timeout";

    /**
     * TaskManagerEvents.
     * 
     * @since 0.1.7
     */
    private TaskManagerEvents() {
    }
}
