/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

/**
 * Agent invoke type enum.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.data.InvokeType}.
 * 
 * @since 0.1.7
 */
public enum InvokeType {
    PROMPT("prompt"),
    LLM("llm"),
    PLUGIN("plugin"),
    WORKFLOW("workflow"),
    CHAIN("chain"),
    RETRIEVER("retriever"),
    EVALUATOR("evalutor");

    private final String value;

    InvokeType(String value) {
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
