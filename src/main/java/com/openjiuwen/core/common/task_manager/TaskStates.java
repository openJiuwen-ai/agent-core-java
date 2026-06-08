/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.Set;

/**
 * Task state collections derived from the task-manager status module.
 *
 * <p>Mirrors Python's terminal-state constants in
 * {@code openjiuwen/core/common/task_manager/types.py}.</p>
 */
public final class TaskStates {

    public static final Set<TaskStatus> TERMINAL_STATES = Set.of(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED,
            TaskStatus.TIMEOUT
    );

    private TaskStates() {
    }
}
