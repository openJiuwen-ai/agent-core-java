/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

/**
 * Minimal factory entrypoint for gateway bootstrap.
 * <p>
 * Mirrors Python's factory-facing bootstrap in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.app.proxy}.
 */
public final class GatewayProxy {

    private GatewayProxy() {
    }

    public static GatewayConfigBootstrap createAppConfig() {
        return new GatewayConfigBootstrap(GatewayBootstrap.buildConfigFromEnv());
    }

    /**
     * Lightweight proxy return type used until real hosting lands in R6-04.
     * <p>
     * Mirrors Python's create_app env bootstrap intent without claiming server parity.
     */
    public record GatewayConfigBootstrap(com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig config) {
    }
}
