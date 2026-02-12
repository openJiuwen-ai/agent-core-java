// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Task executor registry.
 *
 * <p>Manages task executors of different types, supporting dynamic registration
 * and retrieval. Uses task_type to find the corresponding TaskExecutor builder.
 *
 * <p>Python reference: {@code modules/task_scheduler.py::TaskExecutorRegistry}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskExecutorRegistry {

    private final Map<String, Function<TaskExecutorDependencies, TaskExecutor>> taskExecutorBuilders;

    /**
     * Constructs a new empty registry.
     */
    public TaskExecutorRegistry() {
        this.taskExecutorBuilders = new HashMap<>();
    }

    /**
     * Registers a task executor builder for the given task type.
     *
     * @param taskType            the task type identifier
     * @param taskExecutorBuilder the builder function that receives dependencies and returns a TaskExecutor
     */
    public void addTaskExecutor(String taskType,
                                 Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder) {
        taskExecutorBuilders.put(taskType, taskExecutorBuilder);
    }

    /**
     * Removes the task executor for the given task type.
     *
     * @param taskType the task type identifier
     */
    public void removeTaskExecutor(String taskType) {
        taskExecutorBuilders.remove(taskType);
    }

    /**
     * Gets a task executor instance for the given task type.
     *
     * @param taskType     the task type identifier
     * @param dependencies the task executor dependencies
     * @return the task executor instance
     * @throws com.openjiuwen.core.common.exception.BaseError if the task type is not registered
     */
    public TaskExecutor getTaskExecutor(String taskType, TaskExecutorDependencies dependencies) {
        Function<TaskExecutorDependencies, TaskExecutor> executorBuilder =
            taskExecutorBuilders.get(taskType);
        if (executorBuilder == null) {
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_TASK_EXECUTION_ERROR,
                "task executor not found"
            );
        }
        return executorBuilder.apply(dependencies);
    }
}

