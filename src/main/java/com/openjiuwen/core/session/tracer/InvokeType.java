/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

/**
 * Mirrors Python's {@code InvokeType} in
 * {@code openjiuwen/core/session/tracer/data.py}.
 */
public enum InvokeType {
    PROMPT("prompt"),
    LLM("llm"),
    PLUGIN("plugin"),
    WORKFLOW("workflow"),
    CHAIN("chain"),
    RETRIEVER("retriever"),
    EVALUATOR("evaluator");

    private final String value;

    InvokeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
