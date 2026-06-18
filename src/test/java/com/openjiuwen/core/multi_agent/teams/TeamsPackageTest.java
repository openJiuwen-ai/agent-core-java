/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams;

import com.openjiuwen.core.multi_agent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffTeam;
import com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus.SupervisorAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-agent teams package parity tests.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/core/multi_agent/teams/__init__.py}.</p>
 */
class TeamsPackageTest {

    @Test
    void exposesPythonModuleAndAllSymbols() {
        assertEquals("openjiuwen/core/multi_agent/teams/__init__.py", TeamsPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "make_team_session",
                "standalone_invoke_context",
                "standalone_stream_context",
                "HandoffTeam",
                "HandoffTeamConfig",
                "HandoffConfig",
                "HandoffRoute",
                "HandoffSignal",
                "HandoffOrchestrator",
                "TeamInterruptSignal",
                "HierarchicalToolsTeam",
                "HierarchicalMsgbusTeam",
                "HierarchicalTeamConfig",
                "SupervisorAgent"
        ), TeamsPackage.all());
    }

    @Test
    void exportsFunctionsAndResolvesTypes() {
        assertTrue(TeamsPackage.exports("make_team_session"));
        assertTrue(TeamsPackage.exports("standalone_invoke_context"));
        assertTrue(TeamsPackage.exports("standalone_stream_context"));
        assertSame(HandoffTeam.class, TeamsPackage.typeFor("HandoffTeam"));
        assertSame(HandoffConfig.class, TeamsPackage.typeFor("HandoffConfig"));
        assertSame(
                com.openjiuwen.core.multi_agent.teams.hierarchical_tools.HierarchicalTeam.class,
                TeamsPackage.typeFor("HierarchicalToolsTeam")
        );
        assertSame(
                com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus.HierarchicalTeam.class,
                TeamsPackage.typeFor("HierarchicalMsgbusTeam")
        );
        assertSame(SupervisorAgent.class, TeamsPackage.typeFor("SupervisorAgent"));
        assertNull(TeamsPackage.typeFor("make_team_session"));
    }
}
