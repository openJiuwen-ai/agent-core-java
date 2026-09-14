/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's bridge avatar setup tests in
 * {@code tests/unit_tests/agent_teams/agent/test_bridge_avatar_setup.py}.
 */
class BridgeAvatarSetupPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveTeamModeBridgeOnlyStaysDefault() {
        TeamAgentSpec spec = specWithPredefined(List.of(
                member("codex", "Codex", "r", TeamRole.BRIDGE_AGENT)
        ));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("default");
    }

    @Test
    void testResolveTeamModeHumanOnlyStaysDefault() {
        TeamAgentSpec spec = specWithPredefined(List.of(
                member("alice", "Alice", "x", TeamRole.HUMAN_AGENT)
        ));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("default");
    }

    @Test
    void testResolveTeamModeMixedBridgeHumanStillDefault() {
        TeamAgentSpec spec = specWithPredefined(List.of(
                member("codex", "Codex", "r", TeamRole.BRIDGE_AGENT),
                member("alice", "Alice", "x", TeamRole.HUMAN_AGENT)
        ));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("default");
    }

    @Test
    void testResolveTeamModeWithTeammatePredefinedIsHybrid() {
        TeamAgentSpec spec = specWithPredefined(List.of(
                member("alice", "Alice", "x", TeamRole.TEAMMATE)
        ));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("hybrid");
    }

    @Test
    void testResolveTeamModeExplicitOverrideWins() {
        TeamAgentSpec spec = specWithPredefined(List.of(
                member("codex", "Codex", "r", TeamRole.BRIDGE_AGENT)
        ));
        spec.setTeamMode("hybrid");

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("hybrid");
    }

    @Test
    void testBridgeAgentToolSetMatchesTeammate() {
        TeamBackend backend = backend();

        List<String> bridgeNames = toolNames("bridge_agent", backend);
        List<String> teammateNames = toolNames("teammate", backend);

        assertThat(bridgeNames).isEqualTo(teammateNames);
        assertThat(bridgeNames).contains("send_message", "claim_task", "view_task");
    }

    @Test
    void testBridgeAgentHasNoConsultExternalTool() {
        TeamBackend backend = backend();

        List<String> names = toolNames("bridge_agent", backend);

        assertThat(names).doesNotContain("consult_external_agent");
    }

    @Test
    void testSpawnManagerRoleInferenceForBridge() {
        TeamBackend backend = backend();
        backend.buildTeam("bt", "goal", "L", "leader persona").toCompletableFuture().join();

        MemberOpResult result = backend.spawnBridgeAgent(
                "codex",
                "Codex",
                "r",
                null,
                null,
                null,
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(result.isOk()).isTrue();
        assertThat(backend.isBridgeAgent("codex")).isTrue();
        assertThat(backend.isHumanAgent("codex").toCompletableFuture().join()).isFalse();
        Optional<TeamMember> member = backend.getMember("codex").toCompletableFuture().join();
        assertThat(member).isPresent();
        assertThat(member.orElseThrow().getRole()).isEqualTo(com.openjiuwen.agent_teams.schema.TeamRole.BRIDGE_AGENT.value());
    }

    private static TeamAgentSpec specWithPredefined(List<TeamMemberSpec> members) {
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setAgents(Map.of("leader", leader));
        spec.setTeamName("t");
        spec.setEnableBridge(true);
        spec.setEnableHitt(true);
        spec.setPredefinedMembers(members);
        return spec;
    }

    private static TeamMemberSpec member(String name, String displayName, String persona, TeamRole role) {
        TeamMemberSpec spec = new TeamMemberSpec(name, role, persona);
        spec.setDisplayName(displayName);
        return spec;
    }

    private TeamBackend backend() {
        return new TeamBackend(
                "bt",
                "team_leader",
                true,
                new InMemoryTeamDatabase(),
                new RecordingMessager(),
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                true,
                true,
                List.of(),
                null,
                null,
                tempDir,
                "team plan",
                null
        );
    }

    private static List<String> toolNames(String role, TeamBackend backend) {
        return TeamTools.createTeamTools(
                        role,
                        backend,
                        MemberMode.BUILD_MODE.value(),
                        "temporary",
                        null,
                        null,
                        null,
                        "en")
                .stream()
                .map(tool -> tool.card().name())
                .sorted()
                .toList();
    }

    /**
     * Test-only messager collaborator.
     */
    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> publishedMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
