/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

/**
 * Minimal HTTP response wrapper for gateway upstream calls.
 * <p>
 * Mirrors the subset of Python {@code httpx.Response} consumed by gateway code.
 */
public record GatewayHttpResponse(int statusCode, String body) {
}
