/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow;

import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;

import java.util.Map;

/**
 * Alias for {@link End} — exit point component of the workflow.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.End}.
 * 
 * @since 0.1.7
 */
public class EndComponent extends End {
    /**
     * EndComponent.
     * 
     * @since 0.1.7
     */
    public EndComponent() {
        super((EndConfig) null);
    }

    /**
     * EndComponent.
     * 
     * @param confMap confMap
     * @since 0.1.7
     */
    public EndComponent(Map<String, Object> confMap) {
        super(confMap);
    }

    /**
     * EndComponent.
     * 
     * @param conf conf
     * @since 0.1.7
     */
    public EndComponent(EndConfig conf) {
        super(conf);
    }
}
