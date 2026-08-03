/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.interaction.InteractionRouter;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.runtime.RunActionKind;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamOutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams} top-level package facade in
 * {@code openjiuwen/agent_teams/__init__.py}.
 */
class AgentTeamsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_teams/__init__.py", AgentTeamsPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "DEFAULT_LEADER_MEMBER_NAME",
                "DeepAgentSpec",
                "ExternalTeamClient",
                "HUMAN_AGENT_MEMBER_NAME",
                "TEAM_JOIN_ENV",
                "TeamJoinDescriptor",
                "HumanAgentInbox",
                "HumanAgentNotEnabledError",
                "UnknownHumanAgentError",
                "LeaderSpec",
                "ModelPoolEntry",
                "RESERVED_MEMBER_NAMES",
                "StorageSpec",
                "TeamAgentSpec",
                "TransportSpec",
                "USER_PSEUDO_MEMBER_NAME",
                "UserInbox",
                "is_reserved_name",
                "parse_mention",
                "Messager",
                "MessagerPeerConfig",
                "MessagerTransportConfig",
                "RunAction",
                "RunActionKind",
                "TeamAgent",
                "TeamEvent",
                "TeamLifecycle",
                "TeamMemberSpec",
                "TeamOutputSchema",
                "TeamRole",
                "TeamRuntimeActivation",
                "TeamRuntimeContext",
                "TeamRuntimeManager",
                "TeamSpec",
                "InProcessMessager",
                "PyZmqMessager",
                "create_messager",
                "InProcessSpawnHandle",
                "MemoryDatabaseConfig"
        ), AgentTeamsPackage.all());
        assertSame(AgentTeamsPackage.EXPORTED_SYMBOLS, AgentTeamsPackage.all());
    }

    @Test
    void exposesConstantsAndSelectedTypes() {
        assertEquals(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, AgentTeamsPackage.DEFAULT_LEADER_MEMBER_NAME);
        assertEquals(TeamConstants.HUMAN_AGENT_MEMBER_NAME, AgentTeamsPackage.HUMAN_AGENT_MEMBER_NAME);
        assertEquals(TeamConstants.RESERVED_MEMBER_NAMES, AgentTeamsPackage.RESERVED_MEMBER_NAMES);
        assertSame(TeamAgentSpec.class, AgentTeamsPackage.typeFor("TeamAgentSpec"));
        assertSame(TeamOutputSchema.class, AgentTeamsPackage.typeFor("TeamOutputSchema"));
        assertSame(TeamAgent.class, AgentTeamsPackage.typeFor("TeamAgent"));
        assertSame(RunActionKind.class, AgentTeamsPackage.typeFor("RunActionKind"));
        assertSame(Messager.class, AgentTeamsPackage.typeFor("Messager"));
        assertNull(AgentTeamsPackage.typeFor("create_messager"));
        assertTrue(AgentTeamsPackage.exports("parse_mention"));
        assertFalse(AgentTeamsPackage.exports("missing"));
    }

    @Test
    void delegatesInteractionHelpers() {
        assertTrue(AgentTeamsPackage.isReservedName(TeamConstants.USER_PSEUDO_MEMBER_NAME));
        assertFalse(AgentTeamsPackage.isReservedName("worker"));

        assertTrue(AgentTeamsPackage.parseMention("@leader please check").isPresent());
        assertEquals(InteractionRouter.parseMention("@leader please check"), AgentTeamsPackage.parseMention("@leader please check"));
    }
}
