/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    void exposesPythonStatusValues() {
        assertThat(TaskStatus.PENDING.getValue()).isEqualTo("pending");
        assertThat(TaskStatus.RUNNING.getValue()).isEqualTo("running");
        assertThat(TaskStatus.COMPLETED.getValue()).isEqualTo("completed");
        assertThat(TaskStatus.FAILED.getValue()).isEqualTo("failed");
        assertThat(TaskStatus.CANCELLED.getValue()).isEqualTo("cancelled");
        assertThat(TaskStatus.TIMEOUT.getValue()).isEqualTo("timeout");
    }

    @Test
    void terminalStatesMatchPythonFrozenSet() {
        assertThat(TaskStates.TERMINAL_STATES).containsExactlyInAnyOrder(
                TaskStatus.COMPLETED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.TIMEOUT
        );
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TaskStatus.TIMEOUT.isTerminal()).isTrue();
    }
}
