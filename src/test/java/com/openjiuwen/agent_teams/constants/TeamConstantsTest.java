/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.constants;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamConstantsTest {

    @Test
    void reservedMemberNamesMatchPythonModule() {
        assertEquals("human_agent", TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        assertEquals("user", TeamConstants.USER_PSEUDO_MEMBER_NAME);
        assertEquals("team_leader", TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertEquals(
                Set.of("human_agent", "user", "team_leader"),
                TeamConstants.RESERVED_MEMBER_NAMES
        );
    }
}
