/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;

/**
 * Minimal gateway bootstrap composition result.
 * <p>
 * Mirrors Python's app assembly result from
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.app.bootstrap} without
 * translating the FastAPI hosting layer in this batch.
 */
public record GatewayApplication(
        GatewayConfig config,
        Forwarder forwarder,
        UpstreamGatewayClient upstreamClient,
        GatewayTrajectoryRuntime trajectoryRuntime,
        AutoCloseable closeResources
) implements AutoCloseable {

    @Override
    public void close() throws Exception {
        if (closeResources != null) {
            closeResources.close();
        }
    }
}
