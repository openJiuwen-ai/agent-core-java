/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner response type.
 * <p>
 * Mirrors Python's {@code ResponseType} in
 * {@code openjiuwen/core/workflow/components/llm/questioner_comp.py}.
 */
public enum ResponseType {
    REPLY_DIRECTLY("reply_directly");

    private final String value;

    ResponseType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isValid(String value) {
        for (ResponseType t : values()) {
            if (t.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
