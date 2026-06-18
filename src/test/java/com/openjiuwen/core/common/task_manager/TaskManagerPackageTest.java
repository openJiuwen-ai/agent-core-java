/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/common/task_manager/__init__.py}.
 */
class TaskManagerPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(TaskManagerPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/common/task_manager/__init__.py");

        assertThat(TaskManagerPackage.all()).containsExactly(
                "TaskManager",
                "Task",
                "TaskStatus",
                "TERMINAL_STATES",
                "TaskError",
                "TaskNotFoundError",
                "DuplicateTaskError",
                "get_task_manager",
                "create_task",
                "cancel_group",
                "cancel_all",
                "print_task_tree",
                "get_task_group",
                "set_task_group",
                "get_current_task_id",
                "TaskManagerEvents"
        );
    }

    @Test
    void resetTaskGroupIsImportedButNotExported() {
        assertThat(TaskManagerPackage.importsSymbol("reset_task_group")).isTrue();
        assertThat(TaskManagerPackage.exports("reset_task_group")).isFalse();
        assertThat(TaskManagerPackage.sourceFor("reset_task_group"))
                .isEqualTo("openjiuwen.core.common.task_manager.context.reset_task_group");
        assertThat(TaskManagerPackage.javaTypeNameFor("reset_task_group"))
                .isEqualTo("com.openjiuwen.core.common.task_manager.TaskContext#resetTaskGroup");
    }

    @Test
    void keyExportsMapToTranslatedJavaTypes() {
        assertThat(TaskManagerPackage.sourceFor("TaskManager"))
                .isEqualTo("openjiuwen.core.common.task_manager.manager.TaskManager");
        assertThat(TaskManagerPackage.javaTypeNameFor("TaskManager"))
                .isEqualTo("com.openjiuwen.core.common.task_manager.TaskManager");
        assertThat(TaskManagerPackage.javaTypeNameFor("TaskManagerEvents"))
                .isEqualTo("com.openjiuwen.core.runner.callback.TaskManagerEvents");
        assertThat(TaskManagerPackage.javaTypeNameFor("TERMINAL_STATES"))
                .isEqualTo("com.openjiuwen.core.common.task_manager.TaskStates#TERMINAL_STATES");
    }

    @Test
    void unknownSymbolIsNotExposed() {
        assertThat(TaskManagerPackage.importsSymbol("missing")).isFalse();
        assertThat(TaskManagerPackage.exports("missing")).isFalse();
        assertThat(TaskManagerPackage.sourceFor("missing")).isNull();
        assertThat(TaskManagerPackage.javaTypeNameFor("missing")).isNull();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> TaskManagerPackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
