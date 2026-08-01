/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Static convenience functions mirroring the Python module-level helpers.
 * 
 * @since 0.1.7
 */
public final class TaskManagers {
    /**
     * TaskManagers.
     * 
     * @since 0.1.7
     */
    private TaskManagers() {
    }

    /**
     * getTaskManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static TaskManager getTaskManager() {
        return TaskManager.getInstance();
    }

    /**
     * createTask.
     * 
     * @param callable callable
     * @param taskId taskId
     * @param name name
     * @param group group
     * @param timeout timeout
     * @param metadata metadata
     * @param isCatchExceptionsEnabled isCatchExceptionsEnabled
     * @return the result
     * @since 0.1.7
     */
    public static <T> Task createTask(Callable<T> callable, String taskId, String name, String group, Double timeout,
            Map<String, Object> metadata, boolean isCatchExceptionsEnabled) {
        return getTaskManager().createTask(callable, taskId, name, group, timeout, metadata, isCatchExceptionsEnabled);
    }

    /**
     * cancelGroup.
     * 
     * @param group group
     * @return the result
     * @since 0.1.7
     */
    public static int cancelGroup(String group) {
        return getTaskManager().cancelGroup(group);
    }

    /**
     * cancelAll.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static int cancelAll() {
        return getTaskManager().cancelAll();
    }

    /**
     * printTaskTree.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
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
