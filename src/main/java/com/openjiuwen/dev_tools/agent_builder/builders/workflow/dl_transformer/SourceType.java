/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

/**
 * Source type enumeration.
 * <p>
 * Mirrors Python's {@code SourceType} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public enum SourceType {
    ref("ref"),
    constant("constant");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
