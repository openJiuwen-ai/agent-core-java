/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.util.Map;

/**
 * Minimal HTTP response wrapper for gateway upstream calls.
 * <p>
 * Mirrors Python's upstream-response role in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/upstream/upstream_client.py}.
 */
public record GatewayHttpResponse(int statusCode, String body, Map<String, String> headers, String contentType) {

    public GatewayHttpResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public GatewayHttpResponse(int statusCode, String body) {
        this(statusCode, body, Map.of(), null);
    }

    public GatewayHttpResponse(int statusCode, String body, Map<String, String> headers) {
        this(statusCode, body, headers, headers == null ? null : headers.get("content-type"));
    }
}
