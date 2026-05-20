/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

/**
 * Handler name enum for tracer callbacks.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TracerHandlerName}.
 */
public enum TracerHandlerName {
    TRACE_AGENT("tracer_agent"),
    TRACER_WORKFLOW("tracer_workflow");

    private final String value;

    TracerHandlerName(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }
}
