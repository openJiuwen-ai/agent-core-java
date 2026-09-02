/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

/**
 * Controller interface for breaking out of loop execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopController}.
 * 
 * @since 0.1.7
 */
public interface LoopController {
    /**
     * breakLoop.
     * 
     * @since 0.1.7
     */
    void breakLoop();

    /**
     * isBroken.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isBroken();
}
