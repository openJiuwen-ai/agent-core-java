/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

/**
 * Mirrors Python's {@code GatewayResponse} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 *
 * @param code status code
 * @param message status message
 * @param data response payload
 */
public record GatewayResponse(int code, String message, Object data) {
}
