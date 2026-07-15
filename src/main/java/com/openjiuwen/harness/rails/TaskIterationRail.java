/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.task_loop.TaskIterationContext;

/**
 * Public interface TaskIterationRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface TaskIterationRail {
    /**
     * afterTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    default void afterTaskIteration(TaskIterationContext ctx) {
    }
}
