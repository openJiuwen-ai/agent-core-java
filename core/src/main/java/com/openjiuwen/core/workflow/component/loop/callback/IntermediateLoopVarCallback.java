/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loop callback that initializes intermediate loop variables from the session state.
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
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state == null) {
            return null;
        }
        Object localVars = WorkflowSessionSupport.getInputs(session, intermediateLoopVar);
        if (localVars instanceof Map<?, ?> localMap && !localMap.isEmpty()) {
            Map<String, Object> typedLocals = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : localMap.entrySet()) {
                typedLocals.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            state.update(typedLocals);
            WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(session);
            if (commitState != null) {
                commitState.commit();
            }
        }
        if (intermediateLoopVarRoot != null && !intermediateLoopVarRoot.isEmpty()) {
            Map<String, Object> rootedVars = new LinkedHashMap<>();
            rootedVars.put(intermediateLoopVarRoot, localVars);
            return rootedVars;
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
    public Object endRound(BaseSession session, Integer loopTimes) {
        return null;
    }
}
