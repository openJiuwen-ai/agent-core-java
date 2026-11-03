/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream;

/**
 * Gateway-side wrapper for upstream forwarding failures.
 * <p>
 * Mirrors Python's behavior of converting upstream HTTP failures into gateway 502 errors.
 */
public class GatewayForwardingException extends RuntimeException {

    private final int statusCode;

    public GatewayForwardingException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
