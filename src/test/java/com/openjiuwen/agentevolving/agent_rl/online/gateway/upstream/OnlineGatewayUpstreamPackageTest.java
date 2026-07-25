/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlineGatewayUpstreamPackageTest {

    @Test
    void exposesPackageDescription() {
        assertEquals(
                "Gateway upstream transport and forwarding components.",
                OnlineGatewayUpstreamPackage.DESCRIPTION
        );
    }
}
