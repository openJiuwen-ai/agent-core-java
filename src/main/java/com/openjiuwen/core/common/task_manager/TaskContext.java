/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

/**
 * Context helpers for task-group and current-task bindings.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/common/task_manager/context.py}.</p>
 */
public final class TaskContext {

    private static final ThreadLocal<Object> ROOT_TASK_GROUP = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TASK_ID = new InheritableThreadLocal<>();

    private TaskContext() {
    }

    /**
     * Token used to restore the prior ThreadLocal state.
     *
     * <p>Mirrors the restore-token pattern returned by Python ContextVar#set.</p>
     */
    public record ContextToken<T>(T previousValue, boolean hadValue) {
    }

    public static Object getTaskGroup() {
        return ROOT_TASK_GROUP.get();
    }

    public static ContextToken<Object> setTaskGroup(Object taskGroup) {
        Object previous = ROOT_TASK_GROUP.get();
        boolean hadValue = previous != null;
        ROOT_TASK_GROUP.set(taskGroup);
        return new ContextToken<>(previous, hadValue);
    }

    public static void resetTaskGroup(ContextToken<Object> token) {
        reset(ROOT_TASK_GROUP, token);
    }

    public static String getCurrentTaskId() {
        return CURRENT_TASK_ID.get();
    }

    static ContextToken<String> setCurrentTaskId(String taskId) {
        String previous = CURRENT_TASK_ID.get();
        boolean hadValue = previous != null;
        CURRENT_TASK_ID.set(taskId);
        return new ContextToken<>(previous, hadValue);
    }

    static void resetCurrentTaskId(ContextToken<String> token) {
        reset(CURRENT_TASK_ID, token);
    }

    private static <T> void reset(ThreadLocal<T> context, ContextToken<T> token) {
        if (token == null || !token.hadValue()) {
            context.remove();
            return;
        }
        context.set(token.previousValue());
    }
}
