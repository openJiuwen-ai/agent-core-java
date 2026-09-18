/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Outcome of a task-creation mutation.
 *
 * <p>Mirrors Python's {@code TaskCreateResult} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
public record TaskCreateResult(Object task, String reason) {

    public TaskCreateResult {
        reason = reason == null ? "" : reason;
    }

    public boolean ok() {
        return task != null;
    }

    public Object getTaskProperty(String name) {
        if (task == null) {
            throw new IllegalStateException(
                    "TaskCreateResult has no attribute '" + name + "'; task creation failed: " + reason
            );
        }

        String camel = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : List.of(name, "get" + camel, "is" + camel)) {
            try {
                Method method = task.getClass().getMethod(methodName);
                return method.invoke(task);
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor.
            }
        }

        try {
            Field field = task.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(task);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(
                    "TaskCreateResult wrapped task has no attribute '" + name + "'",
                    ex
            );
        }
    }

    public static TaskCreateResult success(Object task) {
        return new TaskCreateResult(task, "");
    }

    public static TaskCreateResult fail(String reason) {
        return new TaskCreateResult(null, reason);
    }
}
