/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow.loop;

import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;

import java.util.Map;

/**
 * Concrete loop component (alias for {@link LoopComponentImpl}).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopComponent}.
 */
public class LoopComponent extends LoopComponentImpl {

    public LoopComponent(com.openjiuwen.core.workflow.component.loop.LoopGroup loopGroup,
                         Map<String, Object> outputSchema) {
        super(loopGroup, outputSchema);
    }
}
