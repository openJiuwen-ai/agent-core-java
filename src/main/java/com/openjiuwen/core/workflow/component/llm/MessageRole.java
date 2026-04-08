/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Role of a message in the LLM conversation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.MessageRole}.
 */
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function");

    private final String value;

    MessageRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
