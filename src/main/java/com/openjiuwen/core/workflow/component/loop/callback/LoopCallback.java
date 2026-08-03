/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.graph.AtomicNode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract loop callback that dispatches to stage-specific methods.
 *
 * <p>Mirrors Python's {@code LoopCallback} in
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/loop_callback.py}.</p>
 */
public abstract class LoopCallback extends AtomicNode {

    public static final String FIRST_LOOP = "first_in_loop";
    public static final String START_ROUND = "start_round";
    public static final String END_ROUND = "end_round";
    public static final String OUT_LOOP = "out_loop";

    /**
     * Call the callback for a given loop stage.
     */
    public void call(String loopStage, BaseSession session, Integer loopTimes) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("loopStage", loopStage);
        kwargs.put("session", session);
        kwargs.put("loopTimes", loopTimes);
        try {
            atomicInvoke(kwargs);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void call(String loopStage, BaseSession session) {
        call(loopStage, session, null);
    }

    @Override
    protected Object atomicInvokeInternal(Map<String, Object> kwargs) {
        String loopStage = (String) kwargs.get("loopStage");
        BaseSession session = (BaseSession) kwargs.get("session");
        Integer loopTimes = kwargs.get("loopTimes") instanceof Number
                ? ((Number) kwargs.get("loopTimes")).intValue() : null;

        Object output;
        switch (loopStage) {
            case FIRST_LOOP:
                output = firstInLoop(session);
                break;
            case START_ROUND:
                output = startRound(session);
                break;
            case END_ROUND:
                output = endRound(session, loopTimes);
                break;
            default:
                output = outLoop(session);
                break;
        }
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (output instanceof Map<?, ?> outputMap && state != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedOutput = (Map<String, Object>) outputMap;
            WorkflowSessionSupport.setOutputs(session, typedOutput);
        }
        return null;
    }

    /**
     * Called once before the first loop iteration.
     */
    public abstract Object firstInLoop(BaseSession session);

    /**
     * Called when the loop exits normally.
     */
    public abstract Object outLoop(BaseSession session);

    /**
     * Called at the start of each loop round.
     */
    public abstract Object startRound(BaseSession session);

    /**
     * Called at the end of each loop round.
     */
    public abstract Object endRound(BaseSession session, Integer loopTimes);
}
