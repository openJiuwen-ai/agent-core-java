/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.harness.DeepAgent;

import java.util.List;

/**
 * Module facade for DeepAgent task-loop runtime components.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/task_loop/__init__.py}.</p>
 */
public final class TaskLoopPackage {

    public static final String DEEP_TASK_TYPE = TaskLoopEventExecutor.DEEP_TASK_TYPE;

    private TaskLoopPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                "DEEP_TASK_TYPE",
                TaskLoopEventExecutor.class,
                "build_deep_executor",
                TaskLoopEventHandler.class,
                LoopCoordinator.class,
                LoopQueues.class,
                TaskLoopController.class
        );
    }

    public static Object getAttribute(String name) {
        return switch (name) {
            case "DEEP_TASK_TYPE" -> DEEP_TASK_TYPE;
            case "TaskLoopEventExecutor" -> TaskLoopEventExecutor.class;
            case "build_deep_executor" -> "build_deep_executor";
            case "TaskLoopEventHandler" -> TaskLoopEventHandler.class;
            case "LoopCoordinator" -> LoopCoordinator.class;
            case "LoopQueues" -> LoopQueues.class;
            case "TaskLoopController" -> TaskLoopController.class;
            default -> throw new IllegalArgumentException(
                    "module 'openjiuwen.harness.task_loop' has no attribute '" + name + "'"
            );
        };
    }

    public static TaskLoopEventExecutor buildDeepExecutor(
            TaskExecutorDependencies dependencies,
            DeepAgent deepAgent
    ) {
        return TaskLoopEventExecutor.buildDeepExecutor(dependencies, deepAgent);
    }
}
