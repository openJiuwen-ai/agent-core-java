/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.WorkflowComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loop component implementation exposing Python's {@code loop_group} property.
 *
 * <p>Mirrors Python's {@code LoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopComponentImpl extends WorkflowComponent implements LoopComponent {

    private final LoopGroup loopGroup;
    private final Map<String, ?> outputSchema;

    public LoopComponentImpl(LoopGroup loopGroup, Map<String, ?> outputSchema) {
        if (loopGroup == null) {
            throw new IllegalArgumentException("loop_group is None");
        }
        this.loopGroup = loopGroup;
        this.outputSchema = outputSchema == null ? Map.of() : new LinkedHashMap<>(outputSchema);
        this.loopGroup.checkValidate();
    }

    @Override
    public HasDrawable getLoopGroup() {
        return loopGroup;
    }

    public Map<String, ?> getOutputSchema() {
        return outputSchema;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }
}
