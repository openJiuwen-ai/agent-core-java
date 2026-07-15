/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner START state.
 * <p>
 * Mirrors Python's {@code QuestionerStartState} – a subclass of {@code QuestionerState}
 * fixed to {@link ExecutionStatus#START}. Transitions: can move to INTERACT or END.
 * 
 * @since 0.1.7
 */
public class QuestionerStartState extends QuestionerState {
    /**
     * QuestionerStartState.
     * 
     * @since 0.1.7
     */
    public QuestionerStartState() {
        super();
        setStatus(ExecutionStatus.START);
    }

    /**
     * Create from an existing {@link QuestionerState}.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    public static QuestionerStartState fromState(QuestionerState state) {
        QuestionerStartState s = new QuestionerStartState();
        s.setResponseNum(state.getResponseNum());
        s.setUserResponse(state.getUserResponse());
        s.setQuestion(state.getQuestion());
        s.setExtractedKeyFields(state.getExtractedKeyFields());
        s.setStatus(ExecutionStatus.START);
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
        return switch (event) {
            case USER_INTERACT_EVENT -> QuestionerInteractState.fromState(this);
            case END_EVENT -> QuestionerEndState.fromState(this);
            default -> this;
        };
    }
}
