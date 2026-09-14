/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

/**
 * Singular-package compatibility facade for the ReAct workflow executable.
 *
 * <p>Mirrors Python's {@code ReActAgentCompExecutable} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_executable.py}.</p>
 */
public class ReActAgentCompExecutable
        extends com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable {

    public ReActAgentCompExecutable(ReActAgentCompConfig config) {
        super(config);
    }
}
