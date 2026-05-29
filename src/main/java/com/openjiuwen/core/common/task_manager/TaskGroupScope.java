/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Scope object for current task-group context.
 * Matches Python's async with manager.task_group() behavior:
 * exceptions from tasks are absorbed on scope exit, allowing
 * post-scope status assertions.
 */
public final class TaskGroupScope implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskGroupScope.class);
    private final String name;
    private final TaskContext.Token token;
    private final TaskManager owner;
    private final List<Exception> suppressedErrors = new ArrayList<>();

    TaskGroupScope(TaskManager owner, String name) {
        this.owner = owner;
        this.name = name != null && !name.isBlank() ? name : "task_group_" + UUID.randomUUID();
        this.token = TaskContext.setTaskGroup(this);
    }

    public String getName() {
        return name;
    }

    public List<Exception> getErrors() {
        return Collections.unmodifiableList(suppressedErrors);
    }

    @Override
    public void close() throws Exception {
        try {
            if (owner != null) {
                owner.waitGroup(name, false);
            }
        } catch (Exception e) {
            suppressedErrors.add(e);
            log.debug("TaskGroupScope '{}' absorbed exception on close: {}", name, e.getMessage());
        } finally {
            TaskContext.resetTaskGroup(token);
        }
    }
}
