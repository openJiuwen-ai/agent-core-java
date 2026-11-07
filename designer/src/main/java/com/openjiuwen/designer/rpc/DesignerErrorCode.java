/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

/**
 * Stable designer RPC error codes.
 *
 * @since 2026-11-06
 */
public enum DesignerErrorCode {
    BAD_REQUEST("BAD_REQUEST"),
    NOT_FOUND("NOT_FOUND"),
    CONFLICT("CONFLICT"),
    NOT_IMPLEMENTED("NOT_IMPLEMENTED"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    private final String wireValue;

    DesignerErrorCode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Return the wire value.
     *
     * @return wire value
     */
    public String wireValue() {
        return wireValue;
    }
}
