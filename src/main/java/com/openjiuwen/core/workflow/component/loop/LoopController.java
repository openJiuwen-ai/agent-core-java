/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.loop;

/**
 * Controller interface for breaking out of loop execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopController}.
 */
public interface LoopController {
    void breakLoop();

    boolean isBroken();
}
