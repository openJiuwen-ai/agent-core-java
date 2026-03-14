/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner USER_INTERACT state.
 * <p>
 * Mirrors Python's {@code QuestionerInteractState} – a subclass of {@code QuestionerState}
 * fixed to {@link ExecutionStatus#USER_INTERACT}. Can only transition to END.
 */
public class QuestionerInteractState extends QuestionerState {

    public QuestionerInteractState() {
        super();
        setStatus(ExecutionStatus.USER_INTERACT);
    }

    /**
     * Create from an existing {@link QuestionerState}.
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

    @Override
    public QuestionerState handleEvent(QuestionerEvent event) {
        if (event == QuestionerEvent.END_EVENT) {
            return QuestionerEndState.fromState(this);
        }
        return this;
    }
}
