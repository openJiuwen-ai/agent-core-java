/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnPayloadBuilder;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for {@link SpawnPayloadBuilder}.
 *
 * <p>Mirrors Python's {@code SpawnPayloadBuilder} in
 * {@code openjiuwen/agent_teams/agent/payload.py}.</p>
 */
class SpawnPayloadBuilderTest {

    @Test
    void spawnPayloadTopLevelAndCoordinationKeysAreFrozen() {
        SpawnPayloadBuilder builder = new SpawnPayloadBuilder(spec(), leaderContext());
        Map<String, Object> payload = builder.buildSpawnPayload(memberContext("worker-a"), "hello");

        assertEquals(Set.of("coordination", "query"), payload.keySet());
        assertEquals("hello", payload.get("query"));
        assertInstanceOf(Map.class, payload.get("coordination"));
        Map<?, ?> coordination = (Map<?, ?>) payload.get("coordination");
        assertEquals(
                Set.of("team_name", "display_name", "leader_member_name", "member_name", "role", "persona",
                        "transport"),
                coordination.keySet()
        );
        assertEquals("team-a", coordination.get("team_name"));
        assertEquals("Team A", coordination.get("display_name"));
        assertEquals("leader", coordination.get("leader_member_name"));
        assertEquals("worker-a", coordination.get("member_name"));
        assertEquals("teammate", coordination.get("role"));
        assertEquals("worker persona", coordination.get("persona"));
        assertNull(coordination.get("transport"));
    }

    @Test
    void spawnPayloadUsesDefaultJoinMessageAndEmptyTeamSpecFallback() {
        TeamAgentSpec spec = spec();
        TeamRuntimeContext leader = new TeamRuntimeContext();
        leader.setRole(TeamRole.LEADER);
        leader.setMemberName("leader");
        SpawnPayloadBuilder builder = new SpawnPayloadBuilder(spec, leader);

        TeamRuntimeContext member = new TeamRuntimeContext();
        member.setRole(TeamRole.TEAMMATE);
        member.setMemberName("worker");

        Map<String, Object> payload = builder.buildSpawnPayload(member, null);

        assertEquals("Join the team and wait for your first assignment.", payload.get("query"));
        Map<?, ?> coordination = (Map<?, ?>) payload.get("coordination");
        assertEquals("", coordination.get("team_name"));
        assertEquals("", coordination.get("display_name"));
        assertNull(coordination.get("leader_member_name"));
    }

    @Test
    void memberMessagerConfigAllocatesStablePortsAndDropsPubsubBind() {
        TeamAgentSpec spec = spec();
        spec.setMetadata(Map.of("teammate_base_port", 17000, "teammate_port_offset", 20));
        TeamRuntimeContext leader = leaderContext();
        MessagerTransportConfig leaderConfig = new MessagerTransportConfig();
        leaderConfig.setMetadata(Map.of("pubsub_bind", true, "keep", "yes"));
        leaderConfig.setPubsubPublishAddr("tcp://pub");
        leaderConfig.setPubsubSubscribeAddr("tcp://sub");
        leader.setMessagerConfig(leaderConfig);
        SpawnPayloadBuilder builder = new SpawnPayloadBuilder(spec, leader);

        MessagerTransportConfig first = builder.buildMemberMessagerConfig("dev");
        MessagerTransportConfig second = builder.buildMemberMessagerConfig("qa");
        MessagerTransportConfig again = builder.buildMemberMessagerConfig("dev");

        assertEquals("tcp://127.0.0.1:17000", first.getDirectAddr());
        assertEquals("tcp://127.0.0.1:17020", second.getDirectAddr());
        assertEquals(first.getDirectAddr(), again.getDirectAddr());
        assertEquals("yes", first.getMetadata().get("keep"));
        assertFalse(first.getMetadata().containsKey("pubsub_bind"));
    }

    @Test
    void spawnConfigPayloadUsesJsonStyleMaps() {
        SpawnPayloadBuilder builder = new SpawnPayloadBuilder(spec(), leaderContext());
        SpawnAgentConfig config = builder.buildSpawnConfig(memberContext("worker-a"));

        assertEquals(AgentConfigurator.SpawnAgentKind.TEAM_AGENT, config.getAgentKind());
        assertInstanceOf(Map.class, config.getPayload().get("spec"));
        assertInstanceOf(Map.class, config.getPayload().get("context"));
        Map<?, ?> context = (Map<?, ?>) config.getPayload().get("context");
        assertEquals("worker-a", context.get("member_name"));
        assertEquals("teammate", context.get("role"));
        assertInstanceOf(Map.class, context.get("team_spec"));
    }

    @Test
    void memberLoggingConfigRewritesOnlyFileSinkTargets() {
        Map<String, Object> fileSink = new LinkedHashMap<>();
        fileSink.put("target", "logs/app.log");
        Map<String, Object> bareFileSink = new LinkedHashMap<>();
        bareFileSink.put("target", "plain.log");
        Map<String, Object> consoleSink = new LinkedHashMap<>();
        consoleSink.put("target", "stdout");
        Map<String, Object> sinks = new LinkedHashMap<>();
        sinks.put("file", fileSink);
        sinks.put("bare", bareFileSink);
        sinks.put("console", consoleSink);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sinks", sinks);

        Map<String, Object> rewritten = SpawnPayloadBuilder.rewriteMemberLoggingConfig(config, "worker-a");
        Map<?, ?> rewrittenSinks = (Map<?, ?>) rewritten.get("sinks");

        assertEquals("logs/teammates/worker-a/app.log", ((Map<?, ?>) rewrittenSinks.get("file")).get("target"));
        assertEquals("teammates/worker-a/plain.log", ((Map<?, ?>) rewrittenSinks.get("bare")).get("target"));
        assertEquals("stdout", ((Map<?, ?>) rewrittenSinks.get("console")).get("target"));
        assertEquals("logs/app.log", fileSink.get("target"));
    }

    private static TeamAgentSpec spec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("team-a");
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        return spec;
    }

    private static TeamRuntimeContext leaderContext() {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        return ctx;
    }

    private static TeamRuntimeContext memberContext(String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName(memberName);
        ctx.setPersona("worker persona");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        return ctx;
    }
}
