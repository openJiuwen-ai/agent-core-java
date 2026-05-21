/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_teams.test_coordination_lifecycle.
 * Tests for CoordinatorLoop lifecycle.
 */
class CoordinationLifecycleTest {

    @Test
    void coordinationEventTypes() {
        // Verify enum values exist
        assertNotNull(Enum.valueOf(TeamEvent.class, "MESSAGE"));
    }

    @Test
    void teamRoleValues() {
        assertEquals("leader", TeamRole.LEADER.getValue());
        assertEquals("member", TeamRole.MEMBER.getValue());
    }
}