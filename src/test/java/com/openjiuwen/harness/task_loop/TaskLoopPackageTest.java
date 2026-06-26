/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/task_loop/__init__.py}.
 */
class TaskLoopPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        "DEEP_TASK_TYPE",
                        TaskLoopEventExecutor.class,
                        "build_deep_executor",
                        TaskLoopEventHandler.class,
                        LoopCoordinator.class,
                        LoopQueues.class,
                        TaskLoopController.class
                ),
                TaskLoopPackage.exports()
        );
    }

    @Test
    void resolvesLazyAttributesAndUnknownNames() {
        assertEquals(TaskLoopEventExecutor.DEEP_TASK_TYPE, TaskLoopPackage.getAttribute("DEEP_TASK_TYPE"));
        assertEquals(TaskLoopEventExecutor.class, TaskLoopPackage.getAttribute("TaskLoopEventExecutor"));
        assertEquals(TaskLoopEventHandler.class, TaskLoopPackage.getAttribute("TaskLoopEventHandler"));
        assertEquals(LoopCoordinator.class, TaskLoopPackage.getAttribute("LoopCoordinator"));
        assertEquals(LoopQueues.class, TaskLoopPackage.getAttribute("LoopQueues"));
        assertEquals(TaskLoopController.class, TaskLoopPackage.getAttribute("TaskLoopController"));

        assertThrows(IllegalArgumentException.class, () -> TaskLoopPackage.getAttribute("missing"));
    }
}
