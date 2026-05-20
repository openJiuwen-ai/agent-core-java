/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.UUID;

/**
 * Scope object for current task-group context.
 */
public final class TaskGroupScope implements AutoCloseable {
    private final String name;
    private final TaskContext.Token token;
    private final TaskManager owner;

    TaskGroupScope(TaskManager owner, String name) {
        this.owner = owner;
        this.name = name != null && !name.isBlank() ? name : "task_group_" + UUID.randomUUID();
        this.token = TaskContext.setTaskGroup(this);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() throws Exception {
        try {
            if (owner != null) {
                owner.waitGroup(name, false);
            }
        } finally {
            TaskContext.resetTaskGroup(token);
        }
    }
}
