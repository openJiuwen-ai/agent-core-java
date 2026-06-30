/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.task_loop.TaskIterationContext;

/**
 * Public interface TaskIterationRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface TaskIterationRail {
    default void afterTaskIteration(TaskIterationContext ctx) {
    }
}
