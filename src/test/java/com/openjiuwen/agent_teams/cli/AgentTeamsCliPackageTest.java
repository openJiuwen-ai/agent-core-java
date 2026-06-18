/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's agent teams CLI package exports in
 * {@code openjiuwen/agent_teams/cli/__init__.py}.
 */
class AgentTeamsCliPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_teams/cli/__init__.py", AgentTeamsCliPackage.PYTHON_MODULE);
        assertEquals(
                List.of("SpecEntry", "SpecRegistry", "TeamCli", "load_spec_yaml", "run_team_cli"),
                AgentTeamsCliPackage.EXPORTED_SYMBOLS
        );
    }

    @Test
    void exportedTypesAndEntrypointsAreAvailable() {
        assertNotNull(SpecEntry.class);
        assertNotNull(SpecRegistry.class);
        assertNotNull(SpecLoader.class);
        assertNotNull(TeamCli.class);
        assertNotNull(TeamCliApp.class);
    }
}
