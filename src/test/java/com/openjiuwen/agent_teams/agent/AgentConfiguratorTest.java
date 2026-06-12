/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.WorkspaceSpec;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeCreatedEvent;
import com.openjiuwen.harness.tools.worktree.WorktreeEventHandler;
import com.openjiuwen.harness.tools.worktree.WorktreeManager;
import com.openjiuwen.harness.tools.worktree.WorktreeRemovedEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link AgentConfigurator}.
 *
 * <p>Mirrors Python's tests around
 * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
 *
 * <p>Mirrors Python's spawn payload contract in
 * {@code openjiuwen/agent_teams/agent/payload.py}.</p>
 */
class AgentConfiguratorTest {

    @Test
    void resolveTeamModeDefaultWhenNoPredefinedMembers() {
        TeamAgentSpec spec = minimalSpec();

        assertEquals("default", AgentConfigurator.resolveTeamMode(spec));
    }

    @Test
    void resolveTeamModeIgnoresHumanAndBridgeAvatarMembers() {
        TeamAgentSpec spec = minimalSpec();
        spec.setPredefinedMembers(List.of(
                new TeamMemberSpec("human", TeamRole.HUMAN_AGENT, "user"),
                new TeamMemberSpec("bridge", TeamRole.BRIDGE_AGENT, "remote")
        ));

        assertEquals("default", AgentConfigurator.resolveTeamMode(spec));
    }

    @Test
    void resolveTeamModeHybridWhenOrdinaryPredefinedTeammateExists() {
        TeamAgentSpec spec = minimalSpec();
        spec.setPredefinedMembers(List.of(new TeamMemberSpec("dev", TeamRole.TEAMMATE, "developer")));

        assertEquals("hybrid", AgentConfigurator.resolveTeamMode(spec));
    }

    @Test
    void resolveTeamModeExplicitOverrideWins() {
        TeamAgentSpec spec = minimalSpec();
        spec.setTeamMode("predefined");
        spec.setPredefinedMembers(List.of(new TeamMemberSpec("dev", TeamRole.TEAMMATE, "developer")));

        assertEquals("predefined", AgentConfigurator._resolveTeamMode(spec));
    }

    @Test
    void setupInfraRewritesMessagerNodeIdForMemberContext() {
        TeamAgentSpec spec = minimalSpec();
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");
        config.setNodeId("leader");
        TeamRuntimeContext ctx = memberContext("teammate-a", TeamRole.TEAMMATE, config);

        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "d"));
        configurator.setupInfra(spec, ctx);

        assertEquals("teammate-a", messagerNodeId(configurator));
        assertEquals("leader", configurator.getCtx().getMessagerConfig().getNodeId());
        assertInstanceOf(ConfiguredTeamBackend.class, configurator.getTeamBackend());
    }

    @Test
    void buildMemberMessagerConfigAllocatesStablePortsAndDropsPubsubBind() {
        TeamAgentSpec spec = minimalSpec();
        spec.setMetadata(Map.of("teammate_base_port", 17000, "teammate_port_offset", 20));
        MessagerTransportConfig leaderConfig = new MessagerTransportConfig();
        leaderConfig.setMetadata(Map.of("pubsub_bind", true, "keep", "yes"));
        leaderConfig.setPubsubPublishAddr("tcp://pub");
        leaderConfig.setPubsubSubscribeAddr("tcp://sub");

        AgentConfigurator configurator = configured(spec, memberContext("leader", TeamRole.LEADER, leaderConfig));

        MessagerTransportConfig first = configurator.buildMemberMessagerConfig("dev");
        MessagerTransportConfig second = configurator.buildMemberMessagerConfig("qa");
        MessagerTransportConfig again = configurator.buildMemberMessagerConfig("dev");

        assertEquals("tcp://127.0.0.1:17000", first.getDirectAddr());
        assertEquals("tcp://127.0.0.1:17020", second.getDirectAddr());
        assertEquals(first.getDirectAddr(), again.getDirectAddr());
        assertEquals("yes", first.getMetadata().get("keep"));
        assertTrue(!first.getMetadata().containsKey("pubsub_bind"));
    }

    @Test
    void buildSpawnPayloadPreservesPythonWireShape() {
        TeamAgentSpec spec = minimalSpec();
        TeamRuntimeContext ctx = memberContext("leader", TeamRole.LEADER, null);
        TeamRuntimeContext member = memberContext("dev", TeamRole.TEAMMATE, null);

        AgentConfigurator configurator = configured(spec, ctx);
        Map<String, Object> payload = configurator.buildSpawnPayload(member, null);

        assertEquals("Join the team and wait for your first assignment.", payload.get("query"));
        Object coordination = payload.get("coordination");
        assertInstanceOf(Map.class, coordination);
        Map<?, ?> coordinationMap = (Map<?, ?>) coordination;
        assertEquals("team", coordinationMap.get("team_name"));
        assertEquals("dev", coordinationMap.get("member_name"));
        assertEquals("teammate", coordinationMap.get("role"));
    }

    @Test
    void createWorktreeManagerMirrorsLifecycleEventsIntoWorkspace() {
        TeamAgentSpec spec = minimalSpec();
        WorktreeConfig worktreeConfig = new WorktreeConfig();
        worktreeConfig.setEnabled(true);
        spec.setWorktree(worktreeConfig);
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "d"));
        RecordingWorkspaceManager workspaceManager = new RecordingWorkspaceManager();
        configurator.setWorkspaceManager(workspaceManager);

        WorktreeManager manager = configurator.createWorktreeManager(spec);
        WorktreeEventHandler handler = worktreeEventHandler(manager);
        handler.handle(new WorktreeCreatedEvent("slug", "/tmp/wt", "owner", null, false)).toCompletableFuture().join();
        handler.handle(new WorktreeRemovedEvent("slug", "/tmp/wt", "owner", null)).toCompletableFuture().join();

        assertEquals("slug:/tmp/wt", workspaceManager.mounted.get());
        assertEquals("slug", workspaceManager.unmounted.get());
    }

    @Test
    void setupAgentAdoptsProvidedRuntimeWithoutBuildingMemory() {
        TeamAgentSpec spec = minimalSpec();
        TeamRuntimeContext ctx = memberContext("dev", TeamRole.TEAMMATE, null);
        AgentConfigurator configurator = configured(spec, ctx);
        MemberRuntime runtime = new AgentConfigurator.ConfiguredMemberRuntime(
                new DeepAgentSpec(),
                ctx,
                new WorkspaceSpec("root", "cn", false),
                null,
                null
        );

        MemberRuntime returned = configurator.setupAgent(spec, ctx, runtime);

        assertSame(runtime, returned);
        assertSame(runtime, configurator.getHarness());
        assertNull(configurator.getMemoryManager());
    }

    @Test
    void buildSpawnConfigCarriesSpecAndContext() {
        TeamAgentSpec spec = minimalSpec();
        TeamRuntimeContext ctx = memberContext("leader", TeamRole.LEADER, null);
        AgentConfigurator configurator = configured(spec, ctx);

        SpawnAgentConfig config = configurator.buildSpawnConfig(ctx);

        assertEquals(AgentConfigurator.SpawnAgentKind.TEAM_AGENT, config.getAgentKind());
        assertInstanceOf(Map.class, config.getPayload().get("spec"));
        assertInstanceOf(Map.class, config.getPayload().get("context"));
        Map<?, ?> contextPayload = (Map<?, ?>) config.getPayload().get("context");
        assertEquals("leader", contextPayload.get("member_name"));
        assertEquals("leader", contextPayload.get("role"));
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setAgents(Map.of("leader", leader));
        spec.setTeamName("team");
        return spec;
    }

    private static TeamRuntimeContext memberContext(
            String memberName,
            TeamRole role,
            MessagerTransportConfig config
    ) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        ctx.setPersona("persona");
        ctx.setTeamSpec(new TeamSpec("team", "Team", "leader"));
        ctx.setMessagerConfig(config);
        return ctx;
    }

    private static AgentConfigurator configured(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "d"));
        configurator.setupInfra(spec, ctx);
        return configurator;
    }

    private static WorktreeEventHandler worktreeEventHandler(WorktreeManager manager) {
        try {
            Field field = WorktreeManager.class.getDeclaredField("eventHandler");
            field.setAccessible(true);
            return (WorktreeEventHandler) field.get(manager);
        } catch (ReflectiveOperationException exc) {
            throw new AssertionError("Unable to inspect WorktreeManager event handler", exc);
        }
    }

    private static String messagerNodeId(AgentConfigurator configurator) {
        try {
            Field field = configurator.getMessager().getClass().getDeclaredField("config");
            field.setAccessible(true);
            MessagerTransportConfig config = (MessagerTransportConfig) field.get(configurator.getMessager());
            return config.getNodeId();
        } catch (ReflectiveOperationException exc) {
            throw new AssertionError("Unable to inspect messager config", exc);
        }
    }

    private static final class RecordingWorkspaceManager extends TeamWorkspaceManager {
        private final AtomicReference<String> mounted = new AtomicReference<>();
        private final AtomicReference<String> unmounted = new AtomicReference<>();

        private RecordingWorkspaceManager() {
            super(new TeamWorkspaceConfig(), "target/workspace", "team");
        }

        @Override
        public void mountWorktree(String slug, String worktreePath) throws IOException {
            mounted.set(slug + ":" + worktreePath);
        }

        @Override
        public void unmountWorktree(String slug) throws IOException {
            unmounted.set(slug);
        }
    }
}
