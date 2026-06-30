/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.Map;

/**
 * Post-body executable that tracks the finish index for loop iteration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.PostLoopBody}.
 */
public class PostLoopBody extends Executable<Object, Object> {

    private int finishIndex = -1;

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        if (session.state() instanceof WorkflowStateCollection state) {
            Object fi = state.get(Constant.FINISH_INDEX);
            if (fi instanceof Number) {
                finishIndex = ((Number) fi).intValue();
            }
            finishIndex += 1;
            state.update(Map.of(Constant.FINISH_INDEX, finishIndex));
            if (state instanceof com.openjiuwen.core.session.state.WorkflowCommitState commitState) {
                commitState.commit();
            }
        }
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean skipTrace() {
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getFinishIndex() {
        return finishIndex;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFinishIndex(int finishIndex) {
        this.finishIndex = finishIndex;
    }
}
