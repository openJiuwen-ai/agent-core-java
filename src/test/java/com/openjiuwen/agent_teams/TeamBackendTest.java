/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.Test;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.TaskStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_teams.test_team.
 * Unit tests for AgentTeam module.
 */
class TeamBackendTest {

    @Test
    void teamRoleEnumValues() {
        TeamRole[] roles = TeamRole.values();
        assertEquals(2, roles.length);
    }

    @Test
    void executionStatusEnumValues() {
        ExecutionStatus[] statuses = ExecutionStatus.values();
        assertTrue(statuses.length > 0);
    }

    @Test
    void taskStatusEnumValues() {
        TaskStatus[] statuses = TaskStatus.values();
        assertTrue(statuses.length > 0);
    }

    @Test
    void memberStatusEnumValues() {
        MemberStatus[] statuses = MemberStatus.values();
        assertTrue(statuses.length > 0);
    }
}