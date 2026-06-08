/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigAndSupportTest {

    @Test
    void gatewayConfigDefaultsMirrorPythonSlice() {
        GatewayConfig config = new GatewayConfig(8080);

        assertEquals(8080, config.getPort());
        assertEquals("127.0.0.1", config.getHost());
        assertEquals("http://127.0.0.1:18000", config.getLlmUrl());
        assertEquals("http://127.0.0.1:18001", config.getJudgeUrl());
        assertEquals(2, config.getUpstreamMaxRetries());
        assertEquals(0.2, config.getUpstreamRetryBackoffSec());
        assertEquals(2.0, config.getUpstreamRetryMaxBackoffSec());
        assertTrue(config.isSingleUserDefault());
    }

    @Test
    void fitListStillWorksWithCurrentGatewayHelpers() {
        assertEquals(List.of(), GatewayCommon.fitList(List.of(1.0, 2.0), 0));
        assertEquals(List.of(1.0, 2.0), GatewayCommon.fitList(List.of(1.0, 2.0, 3.0), 2));
    }
}
