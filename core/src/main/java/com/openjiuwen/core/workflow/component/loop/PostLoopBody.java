/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Post-body executable that tracks the finish index for loop iteration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.PostLoopBody}.
 * Python file: {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.
 */
public class PostLoopBody extends Executable<Object, Object> {

    private int finishIndex = -1;

    private final IntSupplier iterationIndex;

    /**
     * Creates a standalone post-body that advances its own persisted finish index.
     */
    public PostLoopBody() {
        this(null);
    }

    PostLoopBody(IntSupplier iterationIndex) {
        this.iterationIndex = iterationIndex;
    }

    @Override
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state != null) {
            Object fi = state.get(Constant.FINISH_INDEX);
            if (fi instanceof Number) {
                finishIndex = ((Number) fi).intValue();
            }

            // Replaying a committed post-body must not complete the following round.
            finishIndex = iterationIndex == null ? finishIndex + 1 : iterationIndex.getAsInt();
            state.update(Map.of(Constant.FINISH_INDEX, finishIndex));
            com.openjiuwen.core.session.state.WorkflowCommitState commitState =
                    WorkflowSessionSupport.workflowState(session);
            if (commitState != null) {
                commitState.commit();
            }
        }
        return null;
    }

    @Override
    public boolean skipTrace() {
        return true;
    }

    public int getFinishIndex() {
        return finishIndex;
    }

    public void setFinishIndex(int finishIndex) {
        this.finishIndex = finishIndex;
    }
}
