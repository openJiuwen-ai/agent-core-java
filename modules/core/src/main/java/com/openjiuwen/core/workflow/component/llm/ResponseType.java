/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner response type.
 * 
 * @since 0.1.7
 */
public enum ResponseType {
    REPLY_DIRECTLY("reply_directly");

    private final String value;

    ResponseType(String value) {
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

    /**
     * isValid.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static boolean isValid(String value) {
        for (ResponseType t : values()) {
            if (t.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
