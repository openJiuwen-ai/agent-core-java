/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.Objects;

/**
 * Try-with-resources compatibility facade for task groups.
 *
 * <p>Mirrors Python's {@code TaskManager.task_group} in
 * {@code openjiuwen/core/common/task_manager/manager.py}.</p>
 */
public final class TaskGroupContext implements AutoCloseable {

    private final TaskManager.TaskGroupScope scope;

    TaskGroupContext(TaskManager.TaskGroupScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public void cancel() {
        scope.cancel();
    }

    @Override
    public void close() {
        scope.close();
    }
}
