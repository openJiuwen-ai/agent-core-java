/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

/**
 * Controls loop break state.
 *
 * <p>Mirrors Python's {@code LoopController} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public interface LoopController {

    /**
     * Requests loop termination.
     */
    void breakLoop();

    /**
     * Returns whether the loop is broken.
     *
     * @return true when break was requested
     */
    boolean isBroken();
}
