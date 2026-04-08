/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Response type for workflow LLM components.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.WorkflowLLMResponseType}.
 */
public enum WorkflowLLMResponseType {
    JSON("json"),
    MARKDOWN("markdown"),
    TEXT("text");

    private final String value;

    WorkflowLLMResponseType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
