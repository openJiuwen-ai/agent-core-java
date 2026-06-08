/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code TaskManagerEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class TaskManagerEvents {
    public static final String TASK_CREATED = Events.getEvent("task_created");
    public static final String TASK_RUNNING = Events.getEvent("task_running");
    public static final String TASK_COMPLETED = Events.getEvent("task_completed");
    public static final String TASK_FAILED = Events.getEvent("task_failed");
    public static final String TASK_CANCELLED = Events.getEvent("task_cancelled");
    public static final String TASK_TIMEOUT = Events.getEvent("task_timeout");

    private TaskManagerEvents() {
    }
}
