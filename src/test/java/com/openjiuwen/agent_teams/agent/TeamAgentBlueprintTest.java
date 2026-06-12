/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamAgentBlueprint}.
 *
 * <p>Mirrors Python's dataclass properties in
 * {@code openjiuwen/agent_teams/agent/blueprint.py}.</p>
 */
class TeamAgentBlueprintTest {

    @Test
    void exposesConstructionFieldsAsReadOnlyReferences() {
        AgentCard card = new AgentCard("id", "name", "description");
        TeamAgentSpec spec = new TeamAgentSpec();
        TeamRuntimeContext ctx = new TeamRuntimeContext();

        TeamAgentBlueprint blueprint = new TeamAgentBlueprint(card, spec, ctx, "policy", "en");

        assertSame(card, blueprint.getCard());
        assertSame(spec, blueprint.getSpec());
        assertSame(ctx, blueprint.getCtx());
        assertEquals("policy", blueprint.getRolePolicy());
        assertEquals("en", blueprint.getLanguage());
    }

    @Test
    void roleAndMemberNameComeFromRuntimeContext() {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName("dev");

        TeamAgentBlueprint blueprint = new TeamAgentBlueprint(
                new AgentCard("id", "name", "description"),
                new TeamAgentSpec(),
                ctx,
                "",
                "cn"
        );

        assertEquals(TeamRole.TEAMMATE, blueprint.getRole());
        assertEquals("dev", blueprint.getMemberName());
    }

    @Test
    void lifecycleComesFromSpecAndTeamSpecComesFromContext() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setLifecycle("persistent");
        TeamSpec teamSpec = new TeamSpec("team", "Team", "leader");
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setTeamSpec(teamSpec);

        TeamAgentBlueprint blueprint = new TeamAgentBlueprint(
                new AgentCard("id", "name", "description"),
                spec,
                ctx,
                "",
                "cn"
        );

        assertEquals("persistent", blueprint.getLifecycle());
        assertSame(teamSpec, blueprint.getTeamSpec());
    }

    @Test
    void requiredFieldsFailFastLikeFrozenDataclassConstruction() {
        TeamAgentSpec spec = new TeamAgentSpec();
        TeamRuntimeContext ctx = new TeamRuntimeContext();

        assertThrows(NullPointerException.class, () -> new TeamAgentBlueprint(null, spec, ctx, "", "cn"));
        assertThrows(
                NullPointerException.class,
                () -> new TeamAgentBlueprint(new AgentCard("id", "name", "description"), null, ctx, "", "cn")
        );
        assertThrows(
                NullPointerException.class,
                () -> new TeamAgentBlueprint(new AgentCard("id", "name", "description"), spec, null, "", "cn")
        );
    }
}
