/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for task manager lifecycle events.
 * 
 * <p>Mirrors Python's {@code TaskManagerEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class TaskManagerEvents {

    /** Task was created */
    public static final String TASK_CREATED = Events.getEvent("task_created");
    
    /** Task started running */
    public static final String TASK_RUNNING = Events.getEvent("task_running");
    
    /** Task completed successfully */
    public static final String TASK_COMPLETED = Events.getEvent("task_completed");
    
    /** Task failed with an error */
    public static final String TASK_FAILED = Events.getEvent("task_failed");
    
    /** Task was cancelled */
    public static final String TASK_CANCELLED = Events.getEvent("task_cancelled");
    
    /** Task timed out */
    public static final String TASK_TIMEOUT = Events.getEvent("task_timeout");

    private TaskManagerEvents() {
        // Utility class
    }
}