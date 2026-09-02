/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.graph.AtomicNode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.Map;

/**
 * Abstract loop callback that dispatches to stage-specific methods.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.callback.loop_callback.LoopCallback}.
 * 
 * @since 0.1.7
 */
public abstract class LoopCallback extends AtomicNode {
    /**
     * FIRST_LOOP.
     * 
     * @since 0.1.7
     */
    public static final String FIRST_LOOP = "first_in_loop";

    /**
     * START_ROUND.
     * 
     * @since 0.1.7
     */
    public static final String START_ROUND = "start_round";

    /**
     * END_ROUND.
     * 
     * @since 0.1.7
     */
    public static final String END_ROUND = "end_round";

    /**
     * OUT_LOOP.
     * 
     * @since 0.1.7
     */
    public static final String OUT_LOOP = "out_loop";

    /**
     * Call the callback for a given loop stage.
     * 
     * @param loopStage loopStage
     * @param session session
     * @param loopTimes loopTimes
     * @since 0.1.7
     */
    public void call(String loopStage, BaseSession session, Integer loopTimes) {
        atomicInvoke(
                Map.of("loopStage", loopStage, "session", session, "loopTimes", loopTimes != null ? loopTimes : 0));
    }

    /**
     * call.
     * 
     * @param loopStage loopStage
     * @param session session
     * @since 0.1.7
     */
    public void call(String loopStage, BaseSession session) {
        call(loopStage, session, null);
    }

    /**
     * doAtomicInvoke.
     * 
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected Object doAtomicInvoke(Map<String, Object> kwargs) {
        String loopStage = (String) kwargs.get("loopStage");
        BaseSession session = (BaseSession) kwargs.get("session");
        Integer loopTimes =
            kwargs.get("loopTimes") instanceof Number ? ((Number) kwargs.get("loopTimes")).intValue() : null;

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
        if (output != null && session.state() instanceof WorkflowStateCollection) {
            ((WorkflowStateCollection) session.state()).setOutputs(output);
        }
        return null;
    }

    /**
     * Called once before the first loop iteration.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public abstract Object firstInLoop(BaseSession session);

    /**
     * Called when the loop exits normally.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public abstract Object outLoop(BaseSession session);

    /**
     * Called at the start of each loop round.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public abstract Object startRound(BaseSession session);

    /**
     * Called at the end of each loop round.
     * 
     * @param session session
     * @param loopTimes loopTimes
     * @return the result
     * @since 0.1.7
     */
    public abstract Object endRound(BaseSession session, int loopTimes);
}
