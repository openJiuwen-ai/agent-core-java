/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnPayloadBuilder;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cross-process spawn payload wire contract tests.
 *
 * <p>Mirrors Python's {@code test_spawn_payload_contract.py} in
 * {@code tests/unit_tests/agent_teams/test_spawn_payload_contract.py}.</p>
 */
class SpawnPayloadContractTest {

    @Test
    void spawnPayloadTopLevelKeysAreFrozen() {
        SpawnPayloadBuilder builder = makeBuilder();
        TeamRuntimeContext ctx = makeMemberContext("worker_a");

        Map<String, Object> payload = builder.buildSpawnPayload(ctx, null);

        assertEquals(Set.of("coordination", "query"), payload.keySet());
    }

    @Test
    void spawnPayloadCoordinationKeysAreFrozen() {
        SpawnPayloadBuilder builder = makeBuilder();
        TeamRuntimeContext ctx = makeMemberContext("worker_a");

        Map<String, Object> payload = builder.buildSpawnPayload(ctx, "hello");

        assertInstanceOf(Map.class, payload.get("coordination"));
        Map<?, ?> coordination = (Map<?, ?>) payload.get("coordination");
        assertEquals(
                Set.of("team_name", "display_name", "leader_member_name", "member_name", "role", "persona",
                        "transport"),
                coordination.keySet()
        );
        assertEquals("t", coordination.get("team_name"));
        assertEquals("t-display", coordination.get("display_name"));
        assertEquals("leader", coordination.get("leader_member_name"));
        assertEquals("worker_a", coordination.get("member_name"));
        assertEquals("teammate", coordination.get("role"));
        assertEquals("worker persona", coordination.get("persona"));
        assertNull(coordination.get("transport"));
        assertEquals("hello", payload.get("query"));
    }

    @Test
    void spawnPayloadQueryDefaultWhenNoInitialMessage() {
        SpawnPayloadBuilder builder = makeBuilder();
        TeamRuntimeContext ctx = makeMemberContext("worker_a");

        Map<String, Object> payload = builder.buildSpawnPayload(ctx, null);

        assertEquals("Join the team and wait for your first assignment.", payload.get("query"));
    }

    @Test
    void spawnPayloadWithEmptyTeamSpec() {
        TeamAgentSpec spec = makeSpec();
        TeamRuntimeContext leaderContext = new TeamRuntimeContext();
        leaderContext.setRole(TeamRole.LEADER);
        leaderContext.setMemberName("leader");
        SpawnPayloadBuilder builder = new SpawnPayloadBuilder(spec, leaderContext);
        TeamRuntimeContext memberContext = new TeamRuntimeContext();
        memberContext.setRole(TeamRole.TEAMMATE);
        memberContext.setMemberName("worker");

        Map<String, Object> payload = builder.buildSpawnPayload(memberContext, null);

        Map<?, ?> coordination = (Map<?, ?>) payload.get("coordination");
        assertEquals("", coordination.get("team_name"));
        assertEquals("", coordination.get("display_name"));
        assertNull(coordination.get("leader_member_name"));
    }

    @Test
    void memberPortAllocationIsStable() {
        SpawnPayloadBuilder builder = makeBuilder();

        assertNull(builder.buildMemberMessagerConfig("a"));
        assertNull(builder.buildMemberMessagerConfig("a"));
    }

    @Test
    void buildSpawnConfigPayloadHasSpecAndContext() {
        SpawnPayloadBuilder builder = makeBuilder();
        TeamRuntimeContext ctx = makeMemberContext("worker_a");

        SpawnAgentConfig spawnConfig = builder.buildSpawnConfig(ctx);

        assertEquals(Set.of("spec", "context"), spawnConfig.getPayload().keySet());
        assertInstanceOf(Map.class, spawnConfig.getPayload().get("spec"));
        assertInstanceOf(Map.class, spawnConfig.getPayload().get("context"));
        Map<?, ?> context = (Map<?, ?>) spawnConfig.getPayload().get("context");
        assertEquals("worker_a", context.get("member_name"));
        assertEquals("teammate", context.get("role"));
    }

    private static SpawnPayloadBuilder makeBuilder() {
        TeamAgentSpec spec = makeSpec();
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(makeTeamSpec());
        return new SpawnPayloadBuilder(spec, ctx);
    }

    private static TeamAgentSpec makeSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        spec.setTeamName("t");
        return spec;
    }

    private static TeamRuntimeContext makeMemberContext(String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName(memberName);
        ctx.setPersona("worker persona");
        ctx.setTeamSpec(makeTeamSpec());
        return ctx;
    }

    private static TeamSpec makeTeamSpec() {
        return new TeamSpec("t", "t-display", "leader");
    }
}
