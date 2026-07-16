/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Task executor registry.
 * <p>
 * Manages task executors of different types, supporting dynamic registration
 * and retrieval. Uses task_type to find the corresponding TaskExecutor builder.
 * <p>
 * Mirrors Python's {@code TaskExecutorRegistry}.
 * 
 * @since 0.1.7
 */
public class TaskExecutorRegistry {
    private final Map<String, Function<TaskExecutorDependencies, TaskExecutor>> taskExecutorBuilders =
        new ConcurrentHashMap<>();

    /**
     * Register a task executor builder.
     * 
     * @param taskType task type identifier
     * @param taskExecutorBuilder builder that receives dependencies and returns a TaskExecutor
     * @since 0.1.7
     */
    public void addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder) {
        taskExecutorBuilders.put(taskType, taskExecutorBuilder);
    }

    /**
     * Remove a task executor.
     * 
     * @param taskType task type identifier
     * @since 0.1.7
     */
    public void removeTaskExecutor(String taskType) {
        taskExecutorBuilders.remove(taskType);
    }

    /**
     * Get a task executor instance for the given task type.
     * 
     * @param taskType task type identifier
     * @param dependencies task executor dependencies
     * @return task executor instance
     * @since 0.1.7
     */
    public TaskExecutor getTaskExecutor(String taskType, TaskExecutorDependencies dependencies) {
        Function<TaskExecutorDependencies, TaskExecutor> builder = taskExecutorBuilders.get(taskType);
        if (builder == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_EXECUTION_ERROR, "error_msg",
                    "task executor not found for type: " + taskType);
        }
        return builder.apply(dependencies);
    }
}
