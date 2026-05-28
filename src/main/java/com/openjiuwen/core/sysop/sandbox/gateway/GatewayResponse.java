/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.openjiuwen.core.common.exception.StatusCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic response wrapper for gateway operations.
 * <p>
 * Contains status code, message, and optional data payload.
 * <p>
 * Mirrors Python's {@code GatewayResponse} in {@code sandbox/gateway/gateway.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse {

    /** Status code indicating success or failure. */
    private int code;

    /** Human-readable message describing the result. */
    private String message;

    /** Optional data payload (may be any type). */
    private Object data;

    /**
     * Create a success response with data.
     *
     * @param data the response data
     * @return a success GatewayResponse
     */
    public static GatewayResponse success(Object data) {
        return GatewayResponse.builder()
                .code(StatusCode.SUCCESS.getCode())
                .message(StatusCode.SUCCESS.getErrmsg())
                .data(data)
                .build();
    }

    /**
     * Create an error response with message.
     *
     * @param message the error message
     * @return an error GatewayResponse
     */
    public static GatewayResponse error(String message) {
        return GatewayResponse.builder()
                .code(StatusCode.ERROR.getCode())
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Check if the response indicates success.
     *
     * @return true if code is SUCCESS
     */
    public boolean isSuccess() {
        return code == StatusCode.SUCCESS.getCode();
    }
}