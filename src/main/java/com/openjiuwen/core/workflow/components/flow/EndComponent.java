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
 */
public class EndComponent extends End {

    /**
     * Auto-generated for codecheck compliance.
     */
    public EndComponent() {
        super((EndConfig) null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EndComponent(Map<String, Object> confMap) {
        super(confMap);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EndComponent(EndConfig conf) {
        super(conf);
    }
}
