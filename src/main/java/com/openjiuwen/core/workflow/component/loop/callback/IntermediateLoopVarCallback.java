/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.Map;

/**
 * Loop callback that initializes intermediate loop variables from the session state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.callback.intermediate_loop_var.IntermediateLoopVarCallback}.
 */
public class IntermediateLoopVarCallback extends LoopCallback {

    private final Map<String, Object> intermediateLoopVar;
    private final String intermediateLoopVarRoot;

    public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar, String intermediateLoopVarRoot) {
        this.intermediateLoopVar = intermediateLoopVar;
        this.intermediateLoopVarRoot = intermediateLoopVarRoot != null ? intermediateLoopVarRoot : "";
    }

    public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar) {
        this(intermediateLoopVar, "");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object firstInLoop(BaseSession session) {
        if (!(session.state() instanceof WorkflowStateCollection)) {
            return null;
        }
        Object localVars = ((WorkflowStateCollection) session.state()).getInputs(intermediateLoopVar);
        if (intermediateLoopVarRoot != null && !intermediateLoopVarRoot.isEmpty()) {
            return Map.of(intermediateLoopVarRoot, localVars);
        }
        return localVars;
    }

    @Override
    public Object outLoop(BaseSession session) {
        return null;
    }

    @Override
    public Object startRound(BaseSession session) {
        return null;
    }

    @Override
    public Object endRound(BaseSession session, int loopTimes) {
        return null;
    }
}
