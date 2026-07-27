/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Static convenience functions mirroring the Python module-level helpers.
 */
public final class TaskManagers {
    private TaskManagers() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskManager getTaskManager() {
        return TaskManager.getInstance();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static <T> Task createTask(Callable<T> callable,
                                      String taskId,
                                      String name,
                                      String group,
                                      Double timeout,
                                      Map<String, Object> metadata,
                                      boolean isCatchExceptionsEnabled) {
        return getTaskManager().createTask(callable, taskId, name, group, timeout, metadata, isCatchExceptionsEnabled);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int cancelGroup(String group) {
        return getTaskManager().cancelGroup(group);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int cancelAll() {
        return getTaskManager().cancelAll();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String printTaskTree(String taskId) {
        if (taskId != null) {
            return getTaskManager().getTaskTree(taskId);
        }
        StringBuilder builder = new StringBuilder();
        List<Task> tasks = getTaskManager().getRegistry().getAll();
        for (Task task : tasks) {
            if (task.getParentTaskId() == null) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(getTaskManager().getTaskTree(task.getTaskId()));
            }
        }
        return builder.toString();
    }
}
