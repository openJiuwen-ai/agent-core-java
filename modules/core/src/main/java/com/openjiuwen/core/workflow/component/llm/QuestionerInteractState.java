/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner USER_INTERACT state.
 * <p>
 * Mirrors Python's {@code QuestionerInteractState} – a subclass of {@code QuestionerState}
 * fixed to {@link ExecutionStatus#USER_INTERACT}. Can only transition to END.
 * 
 * @since 0.1.7
 */
public class QuestionerInteractState extends QuestionerState {
    /**
     * QuestionerInteractState.
     * 
     * @since 0.1.7
     */
    public QuestionerInteractState() {
        super();
        setStatus(ExecutionStatus.USER_INTERACT);
    }

    /**
     * Create from an existing {@link QuestionerState}.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    public static QuestionerInteractState fromState(QuestionerState state) {
        QuestionerInteractState s = new QuestionerInteractState();
        s.setResponseNum(state.getResponseNum());
        s.setUserResponse(state.getUserResponse());
        s.setQuestion(state.getQuestion());
        s.setExtractedKeyFields(state.getExtractedKeyFields());
        s.setStatus(ExecutionStatus.USER_INTERACT);
        return s;
    }

    /**
     * handleEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    @Override
    public QuestionerState handleEvent(QuestionerEvent event) {
        if (event == QuestionerEvent.END_EVENT) {
            return QuestionerEndState.fromState(this);
        }
        return this;
    }
}
