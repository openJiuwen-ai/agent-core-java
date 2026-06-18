/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel;
import com.openjiuwen.agent_teams.agent.coordination.EventBus;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for the agent package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.agent} package facade in
 * {@code openjiuwen/agent_teams/agent/__init__.py}.</p>
 */
class AgentPackageTest {

    @Test
    void allPreservesPythonExportOrder() {
        assertEquals("openjiuwen/agent_teams/agent/__init__.py", AgentPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "AgentConfigurator",
                "CoordinationKernel",
                "EventBus",
                "RecoveryManager",
                "SessionManager",
                "SpawnManager",
                "StreamController",
                "TeamAgent"
        ), AgentPackage.all());
    }

    @Test
    void exportedSymbolsPointToTranslatedJavaTypes() {
        assertTrue(AgentPackage.exports("AgentConfigurator"));
        assertTrue(AgentPackage.exports("CoordinationKernel"));
        assertTrue(AgentPackage.exports("TeamAgent"));
        assertTrue(AgentPackage.translated("StreamController"));

        assertEquals(
                "openjiuwen.agent_teams.agent.agent_configurator.AgentConfigurator",
                AgentPackage.sourceFor("AgentConfigurator")
        );
        assertEquals(
                "openjiuwen.agent_teams.agent.coordination.CoordinationKernel",
                AgentPackage.sourceFor("CoordinationKernel")
        );
        assertEquals(
                "openjiuwen.agent_teams.agent.team_agent.TeamAgent",
                AgentPackage.sourceFor("TeamAgent")
        );

        assertEquals(
                "com.openjiuwen.agent_teams.agent.AgentConfigurator",
                AgentPackage.getAttr("AgentConfigurator")
        );
        assertEquals(
                "com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel",
                AgentPackage.getAttr("CoordinationKernel")
        );
        assertEquals(
                "com.openjiuwen.agent_teams.agent.SpawnManager",
                AgentPackage.getAttr("SpawnManager")
        );

        assertEquals(AgentConfigurator.class, AgentPackage.resolveType("AgentConfigurator").orElseThrow());
        assertEquals(CoordinationKernel.class, AgentPackage.resolveType("CoordinationKernel").orElseThrow());
        assertEquals(EventBus.class, AgentPackage.resolveType("EventBus").orElseThrow());
        assertEquals(RecoveryManager.class, AgentPackage.resolveType("RecoveryManager").orElseThrow());
        assertEquals(SessionManager.class, AgentPackage.resolveType("SessionManager").orElseThrow());
        assertEquals(SpawnManager.class, AgentPackage.resolveType("SpawnManager").orElseThrow());
        assertEquals(StreamController.class, AgentPackage.resolveType("StreamController").orElseThrow());
        assertEquals(TeamAgent.class, AgentPackage.resolveType("TeamAgent").orElseThrow());
    }

    @Test
    void getAttrRejectsMissingAttributesLikePythonFacade() {
        NoSuchElementException missing = assertThrows(
                NoSuchElementException.class,
                () -> AgentPackage.getAttr("missing")
        );

        assertTrue(missing.getMessage().contains("has no attribute 'missing'"));
    }
}
