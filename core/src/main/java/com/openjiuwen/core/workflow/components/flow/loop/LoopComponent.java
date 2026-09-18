/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow.loop;

import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;

import java.util.Map;

/**
 * Public package alias for the loop component implementation.
 *
 * <p>Mirrors Python's {@code LoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopComponent extends LoopComponentImpl {

    public LoopComponent(LoopGroup loopGroup, Map<String, ?> outputSchema) {
        super(loopGroup, outputSchema);
    }
}
