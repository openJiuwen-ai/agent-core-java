/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

/**
 * Thread-local task-manager context.
 * 
 * @since 0.1.7
 */
public final class TaskContext {
    private static final ThreadLocal<String> CURRENT_TASK_ID = new ThreadLocal<>();

    /**
     * ThreadLocal<>.
     * 
     * @since 0.1.7
     */
    private static final ThreadLocal<TaskGroupScope> ROOT_TASK_GROUP = new ThreadLocal<>();

    /**
     * Public record Token used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record Token(TaskGroupScope previousGroup) {
    }

    /**
     * TaskContext.
     * 
     * @since 0.1.7
     */
    private TaskContext() {
    }

    /**
     * getTaskGroup.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static TaskGroupScope getTaskGroup() {
        return ROOT_TASK_GROUP.get();
    }

    /**
     * setTaskGroup.
     * 
     * @param group group
     * @return the result
     * @since 0.1.7
     */
    public static Token setTaskGroup(TaskGroupScope group) {
        TaskGroupScope previous = ROOT_TASK_GROUP.get();
        ROOT_TASK_GROUP.set(group);
        return new Token(previous);
    }

    /**
     * resetTaskGroup.
     * 
     * @param token token
     * @since 0.1.7
     */
    public static void resetTaskGroup(Token token) {
        ROOT_TASK_GROUP.set(token != null ? token.previousGroup() : null);
    }

    /**
     * getCurrentTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getCurrentTaskId() {
        return CURRENT_TASK_ID.get();
    }

    static String setCurrentTaskId(String taskId) {
        String previous = CURRENT_TASK_ID.get();
        CURRENT_TASK_ID.set(taskId);
        return previous;
    }

    static void resetCurrentTaskId(String previous) {
        if (previous == null) {
            CURRENT_TASK_ID.remove();
        } else {
            CURRENT_TASK_ID.set(previous);
        }
    }
}
