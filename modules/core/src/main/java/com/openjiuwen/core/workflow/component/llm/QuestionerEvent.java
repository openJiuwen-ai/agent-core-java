/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner event types for state transitions.
 * 
 * @since 0.1.7
 */
public enum QuestionerEvent {
    START_EVENT("start"),
    END_EVENT("end"),
    USER_INTERACT_EVENT("user_interact");

    private final String value;

    QuestionerEvent(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
