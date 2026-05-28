/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.HeartbeatRail;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HeartbeatRail.
 */
class TestHeartbeatRail {

    @Test
    @Tag("level0")
    @DisplayName("HeartbeatRail sends heartbeat signals")
    void testHeartbeatRailSendsSignals() {
        HeartbeatRail rail = new HeartbeatRail();
        assertNotNull(rail, "HeartbeatRail should be constructable");
        assertEquals(80, HeartbeatRail.PRIORITY, "Priority should be 80");
        assertTrue(rail instanceof com.openjiuwen.harness.rails.DeepAgentRail,
            "HeartbeatRail should extend DeepAgentRail");
    }
}