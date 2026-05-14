/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal gateway service config for launcher helpers.
 * <p>
 * Mirrors the deterministic fields used from Python's GatewayServiceConfig.
 */
public record GatewayServiceConfig(
        String host,
        int port,
        String redisUrl,
        String recordDir,
        String logLevel,
        double healthTimeout,
        boolean disableTrajectoryCollection
) {
}
