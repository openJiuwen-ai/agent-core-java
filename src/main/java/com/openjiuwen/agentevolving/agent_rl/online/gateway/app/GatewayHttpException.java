/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

/**
 * Lightweight HTTP error used by gateway app helpers.
 * <p>
 * Mirrors Python's helper exceptions in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/http_helpers.py}.
 */
public final class GatewayHttpException extends RuntimeException {

    private final int statusCode;
    private final String detail;

    public GatewayHttpException(int statusCode, String detail) {
        super(detail);
        this.statusCode = statusCode;
        this.detail = detail;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getDetail() {
        return detail;
    }
}
