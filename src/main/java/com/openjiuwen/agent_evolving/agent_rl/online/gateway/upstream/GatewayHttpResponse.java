/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

/**
 * Minimal HTTP response wrapper for gateway upstream calls.
 * <p>
 * Mirrors Python's upstream-response role in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/upstream/upstream_client.py}.
 */
public record GatewayHttpResponse(int statusCode, String body) {
}
