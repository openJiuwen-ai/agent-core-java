/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.agent_rl.offline.OfflineRlPackage;
import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.OfflineCoordinatorPackage;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.OfflineStorePackage;
import com.openjiuwen.agent_evolving.agent_rl.online.OnlineRlPackage;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.OnlineGatewayPackage;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.app.OnlineGatewayAppPackage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRlPackageMarkersTest {

    @Test
    void offlinePackageDescriptionMatchesPythonDocstring() {
        assertEquals("Offline RL training modules.", OfflineRlPackage.DESCRIPTION);
    }

    @Test
    void offlineCoordinatorDescriptionMatchesPythonDocstring() {
        assertEquals(
                "Rollout coordination and data assembly for offline RL training.",
                OfflineCoordinatorPackage.DESCRIPTION
        );
    }

    @Test
    void offlineStoreDescriptionMatchesPythonDocstring() {
        assertEquals("Rollout persistence for offline RL training.", OfflineStorePackage.DESCRIPTION);
    }

    @Test
    void onlinePackageDescriptionMatchesPythonDocstring() {
        assertEquals("Online RL package.", OnlineRlPackage.DESCRIPTION);
    }

    @Test
    void onlineGatewayDescriptionMatchesPythonDocstring() {
        assertEquals(
                "Online-RL Gateway: per-turn trajectory recording with LLM-as-Judge.",
                OnlineGatewayPackage.DESCRIPTION
        );
    }

    @Test
    void onlineGatewayAppDescriptionMatchesPythonDocstring() {
        assertEquals("Gateway app package.", OnlineGatewayAppPackage.DESCRIPTION);
    }
}
