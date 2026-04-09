/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow;

/**
 * Types of data chunks produced during workflow execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowChunkType}.
 */
public enum WorkflowChunkType {
    INTERACTION("interaction"),
    OUTPUT("output"),
    ERROR("error");

    private final String value;

    WorkflowChunkType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
