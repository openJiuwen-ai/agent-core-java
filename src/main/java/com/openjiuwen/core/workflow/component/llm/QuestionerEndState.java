/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner END state.
 * <p>
 * Mirrors Python's {@code QuestionerEndState} – a subclass of {@code QuestionerState}
 * fixed to {@link ExecutionStatus#END}. Can loop back to START via START_EVENT.
 */
public class QuestionerEndState extends QuestionerState {

    public QuestionerEndState() {
        super();
        setStatus(ExecutionStatus.END);
    }

    /**
     * Create from an existing {@link QuestionerState}.
     */
    public static QuestionerEndState fromState(QuestionerState state) {
        QuestionerEndState s = new QuestionerEndState();
        s.setResponseNum(state.getResponseNum());
        s.setUserResponse(state.getUserResponse());
        s.setQuestion(state.getQuestion());
        s.setExtractedKeyFields(state.getExtractedKeyFields());
        s.setStatus(ExecutionStatus.END);
        return s;
    }

    @Override
    public QuestionerState handleEvent(QuestionerEvent event) {
        if (event == QuestionerEvent.START_EVENT) {
            return new QuestionerState().handleEvent(event);
        }
        return this;
    }
}
