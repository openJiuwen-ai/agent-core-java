/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskContextTest {

    @Test
    void setAndResetTaskGroupRestoresPreviousBinding() {
        TaskContext.ContextToken<Object> first = TaskContext.setTaskGroup("group-a");
        TaskContext.ContextToken<Object> second = TaskContext.setTaskGroup("group-b");

        assertThat(TaskContext.getTaskGroup()).isEqualTo("group-b");

        TaskContext.resetTaskGroup(second);
        assertThat(TaskContext.getTaskGroup()).isEqualTo("group-a");

        TaskContext.resetTaskGroup(first);
        assertThat(TaskContext.getTaskGroup()).isNull();
    }

    @Test
    void currentTaskIdDefaultsToNullAndSupportsReset() {
        assertThat(TaskContext.getCurrentTaskId()).isNull();

        TaskContext.ContextToken<String> first = TaskContext.setCurrentTaskId("task-a");
        TaskContext.ContextToken<String> second = TaskContext.setCurrentTaskId("task-b");

        assertThat(TaskContext.getCurrentTaskId()).isEqualTo("task-b");

        TaskContext.resetCurrentTaskId(second);
        assertThat(TaskContext.getCurrentTaskId()).isEqualTo("task-a");

        TaskContext.resetCurrentTaskId(first);
        assertThat(TaskContext.getCurrentTaskId()).isNull();
    }
}
