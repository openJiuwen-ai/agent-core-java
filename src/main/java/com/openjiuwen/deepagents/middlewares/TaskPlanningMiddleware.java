/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.middlewares;

/**
 * Deprecated compatibility placeholder for the removed deepagents middleware.
 *
 * <p>Python `0.1.12` moved active behavior into {@code openjiuwen.harness}.
 */
@Deprecated(forRemoval = false)
public class TaskPlanningMiddleware {

    /**
     * Creates a new TaskPlanningMiddleware instance.
     */
    public TaskPlanningMiddleware() {
        // Placeholder constructor
    }

    /**
     * Plans tasks for the given input.
     *
     * @param task the task to plan
     * @return the planned task result (placeholder)
     */
    public Object plan(Object task) {
        throw new UnsupportedOperationException(
                "TaskPlanningMiddleware is deprecated. Migrate to com.openjiuwen.harness rails.");
    }
}
