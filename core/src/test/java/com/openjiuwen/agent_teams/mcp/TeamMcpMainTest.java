/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.mcp.__main__} in
 * {@code openjiuwen/agent_teams/mcp/__main__.py}.
 */
class TeamMcpMainTest {

    @Test
    void exposesModuleEntrypoint() throws NoSuchMethodException {
        assertEquals("openjiuwen/agent_teams/mcp/__main__.py", TeamMcpMain.PYTHON_MODULE);
        assertNotNull(TeamMcpMain.class.getMethod("main", String[].class));
    }
}
