/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal Jiuwen config for launcher helpers.
 * <p>
 * Mirrors the deterministic fields used from Python's JiuwenConfig.
 */
public record JiuwenConfig(boolean enabled, int agentServerPort, String appHost, int wsPort, String webHost, int webPort) {
}
