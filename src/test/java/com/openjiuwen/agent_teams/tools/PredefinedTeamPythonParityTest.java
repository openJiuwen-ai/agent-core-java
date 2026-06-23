/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.prompts.PromptPolicy;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.TeamTools.TeamTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for predefined team behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_predefined_team} in
 * {@code tests/unit_tests/agent_teams/test_predefined_team.py}.</p>
 */
class PredefinedTeamPythonParityTest {

    private static final String PREDEFINED_TEAM_MODE = "\u9884\u5b9a\u4e49\u56e2\u961f\u6a21\u5f0f";
    private static final String HYBRID_TEAM_MODE = "\u6df7\u5408\u56e2\u961f\u6a21\u5f0f";

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void testTeamAgentSpecDefaultsTeammateModeToBuildMode() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of());

        assertThat(spec.isEnableTeamPlan()).isFalse();
        assertThat(spec.getTeammateMode()).isEqualTo(MemberMode.BUILD_MODE.value());
    }

    @Test
    void testBuildTeamRegistersPredefinedMembers() {
        Fixture fixture = predefinedFixture();

        join(fixture.backend().buildTeam("Test Team", "A predefined team", "Leader", "PM"));

        List<TeamMember> members = join(fixture.database().getTeamMembers("predefined_team", null));
        assertThat(members).extracting(TeamMember::getMemberName)
                .containsExactlyInAnyOrder("leader1", "backend-dev", "frontend-dev");
        assertThat(members).hasSize(3);
    }

    @Test
    void testPredefinedMembersStatusIsUnstarted() {
        Fixture fixture = predefinedFixture();

        join(fixture.backend().buildTeam("Test Team", "desc", "Leader", "PM"));

        TeamMember backendDev = member(fixture, "backend-dev");
        TeamMember frontendDev = member(fixture, "frontend-dev");
        assertThat(backendDev.getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
        assertThat(frontendDev.getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
        assertThat(backendDev.getExecutionStatus()).isEqualTo(ExecutionStatus.IDLE.value());
        assertThat(frontendDev.getExecutionStatus()).isEqualTo(ExecutionStatus.IDLE.value());
    }

    @Test
    void testPredefinedMembersPreserveDescAndPrompt() {
        Fixture fixture = predefinedFixture();

        join(fixture.backend().buildTeam("Test Team", "desc", "Leader", "PM"));

        TeamMember backendDev = member(fixture, "backend-dev");
        TeamMember frontendDev = member(fixture, "frontend-dev");
        assertThat(backendDev.getDesc()).isEqualTo("Senior backend engineer");
        assertThat(backendDev.getPrompt()).isEqualTo("Check tasks and start working");
        assertThat(frontendDev.getDesc()).isEqualTo("Senior frontend engineer");
        assertThat(frontendDev.getPrompt()).isNull();
    }

    @Test
    void testLeaderStillRegisteredAsBusy() {
        Fixture fixture = predefinedFixture();

        join(fixture.backend().buildTeam("Test Team", "desc", "Leader", "PM"));

        TeamMember leader = member(fixture, "leader1");
        assertThat(leader.getStatus()).isEqualTo(MemberStatus.BUSY.value());
        assertThat(leader.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING.value());
    }

    @Test
    void testBuildTeamOnlyRegistersLeader() {
        Fixture fixture = fixture("auto_team", List.of());

        join(fixture.backend().buildTeam("Auto Team", "desc", "Leader", "PM"));

        List<TeamMember> members = join(fixture.database().getTeamMembers("auto_team", null));
        assertThat(members).singleElement()
                .extracting(TeamMember::getMemberName)
                .isEqualTo("leader1");
    }

    @Test
    void testExcludeSpawnMemberWhenPredefined() {
        Fixture fixture = predefinedFixture();

        Set<String> toolNames = toolNames(createTools(
                "leader",
                fixture.backend(),
                MemberMode.BUILD_MODE.value(),
                Set.of("spawn_member")));

        assertThat(toolNames).doesNotContain("spawn_member");
        assertThat(toolNames).contains("build_team", "shutdown_member", "create_task");
    }

    @Test
    void testNoExclusionWithoutPredefined() {
        Fixture fixture = fixture("auto_team", List.of());

        Set<String> toolNames = toolNames(createTools(
                "leader",
                fixture.backend(),
                MemberMode.BUILD_MODE.value(),
                null));

        assertThat(toolNames).contains("spawn_member");
    }

    @Test
    void testExcludeDoesNotAffectTeammateTools() {
        Fixture fixture = predefinedFixture();

        Set<String> toolNames = toolNames(createTools(
                "teammate",
                fixture.backend(),
                MemberMode.BUILD_MODE.value(),
                Set.of("spawn_member")));

        assertThat(toolNames).contains("claim_task");
    }

    @Test
    void testLeaderHasApprovalToolsInPlanMode() {
        Fixture fixture = predefinedFixture();

        Set<String> toolNames = toolNames(createTools(
                "leader",
                fixture.backend(),
                MemberMode.PLAN_MODE.value(),
                null));

        assertThat(toolNames).contains("approve_plan", "approve_tool");
    }

    @Test
    void testLeaderNoApprovalToolsInBuildMode() {
        Fixture fixture = predefinedFixture();

        Set<String> toolNames = toolNames(createTools(
                "leader",
                fixture.backend(),
                MemberMode.BUILD_MODE.value(),
                null));

        assertThat(toolNames).doesNotContain("approve_plan", "approve_tool");
    }

    @Test
    void testTeammateDoesNotHaveApprovalTools() {
        Fixture fixture = predefinedFixture();

        for (String mode : List.of(MemberMode.BUILD_MODE.value(), MemberMode.PLAN_MODE.value())) {
            Set<String> toolNames = toolNames(createTools("teammate", fixture.backend(), mode, null));
            assertThat(toolNames).doesNotContain("approve_plan", "approve_tool");
            if (MemberMode.PLAN_MODE.value().equals(mode)) {
                assertThat(toolNames).contains("submit_plan");
            } else {
                assertThat(toolNames).doesNotContain("submit_plan");
            }
        }
    }

    @Test
    void testTeamPlanUsesTeammateMode() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of());
        spec.setEnableTeamPlan(true);
        spec.setTeammateMode(MemberMode.PLAN_MODE.value());

        assertThat(spec.getTeammateMode()).isEqualTo(MemberMode.PLAN_MODE.value());
    }

    @Test
    void testPredefinedPromptIncludesOverride() {
        String prompt = promptFor(AgentConfigurator.TeamRole.LEADER, "PM", "predefined");

        assertThat(prompt).contains(PREDEFINED_TEAM_MODE, "spawn_member");
    }

    @Test
    void testAutoTeamPromptNoOverride() {
        String prompt = promptFor(AgentConfigurator.TeamRole.LEADER, "PM", "default");

        assertThat(prompt).doesNotContain(PREDEFINED_TEAM_MODE);
    }

    @Test
    void testHybridPromptIncludesHybridMode() {
        String prompt = promptFor(AgentConfigurator.TeamRole.LEADER, "PM", "hybrid");

        assertThat(prompt).contains(HYBRID_TEAM_MODE, "spawn_member");
    }

    @Test
    void testPredefinedWorkflowNotAppliedToTeammate() {
        String prompt = promptFor(AgentConfigurator.TeamRole.TEAMMATE, "Dev", "predefined");

        assertThat(prompt).doesNotContain(PREDEFINED_TEAM_MODE);
    }

    @Test
    void testResolveByMemberNameFirst() {
        DeepAgentSpec leaderSpec = deepAgentSpec(100);
        DeepAgentSpec teammateSpec = deepAgentSpec(50);
        DeepAgentSpec customSpec = deepAgentSpec(30);
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", leaderSpec,
                "teammate", teammateSpec,
                "custom-member", customSpec
        ));

        AgentConfigurator.DeepAgentSpec resolved = resolveAgentSpec(spec, AgentConfigurator.TeamRole.TEAMMATE,
                "custom-member");

        assertThat(resolved).isSameAs(customSpec);
        assertThat(((DeepAgentSpec) resolved).getMaxIterations()).isEqualTo(30);
    }

    @Test
    void testFallbackToRoleValue() {
        DeepAgentSpec leaderSpec = deepAgentSpec(100);
        DeepAgentSpec teammateSpec = deepAgentSpec(50);
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", leaderSpec,
                "teammate", teammateSpec
        ));

        AgentConfigurator.DeepAgentSpec resolved = resolveAgentSpec(spec, AgentConfigurator.TeamRole.TEAMMATE,
                "unknown-member");

        assertThat(resolved).isSameAs(teammateSpec);
        assertThat(((DeepAgentSpec) resolved).getMaxIterations()).isEqualTo(50);
    }

    @Test
    void testFallbackChainToLeader() {
        DeepAgentSpec leaderSpec = deepAgentSpec(100);
        TeamAgentSpec spec = specWithAgents(Map.of("leader", leaderSpec));

        AgentConfigurator.DeepAgentSpec resolved = resolveAgentSpec(spec, AgentConfigurator.TeamRole.TEAMMATE,
                "unknown-member");

        assertThat(resolved).isSameAs(leaderSpec);
        assertThat(((DeepAgentSpec) resolved).getMaxIterations()).isEqualTo(100);
    }

    @Test
    void testLeaderRoleUsesLeaderSpec() {
        DeepAgentSpec leaderSpec = deepAgentSpec(100);
        DeepAgentSpec teammateSpec = deepAgentSpec(50);
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", leaderSpec,
                "teammate", teammateSpec
        ));

        AgentConfigurator.DeepAgentSpec resolved = resolveAgentSpec(spec, AgentConfigurator.TeamRole.LEADER, null);

        assertThat(resolved).isSameAs(leaderSpec);
        assertThat(((DeepAgentSpec) resolved).getMaxIterations()).isEqualTo(100);
    }

    private static Fixture predefinedFixture() {
        return fixture("predefined_team", predefinedMembers());
    }

    private static Fixture fixture(String teamName, List<TeamMemberSpec> predefinedMembers) {
        AgentTeamsContext.setSessionId("session_id");
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = new TeamBackend(
                teamName,
                "leader1",
                true,
                database,
                messager,
                MemberMode.BUILD_MODE,
                predefinedMembers,
                null,
                null,
                false,
                false,
                List.of(),
                null,
                null,
                null,
                "team-plan",
                "leader1"
        );
        return new Fixture(database, backend);
    }

    private static List<TeamMemberSpec> predefinedMembers() {
        TeamMemberSpec backend = new TeamMemberSpec(
                "backend-dev",
                "Backend Developer",
                TeamRole.TEAMMATE,
                "Senior backend engineer"
        );
        backend.setPromptHint("Check tasks and start working");

        TeamMemberSpec frontend = new TeamMemberSpec(
                "frontend-dev",
                "Frontend Developer",
                TeamRole.TEAMMATE,
                "Senior frontend engineer"
        );
        return List.of(backend, frontend);
    }

    private static TeamMember member(Fixture fixture, String memberName) {
        return join(fixture.database().getMember(memberName, fixture.backend().getTeamName())).orElseThrow();
    }

    private static List<TeamTool> createTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools) {
        return TeamTools.createTeamTools(
                role,
                backend,
                teammateMode,
                "temporary",
                null,
                null,
                excludeTools,
                "cn");
    }

    private static Set<String> toolNames(List<TeamTool> tools) {
        return tools.stream()
                .map(tool -> tool.card().name())
                .collect(Collectors.toSet());
    }

    private static String promptFor(AgentConfigurator.TeamRole role, String persona, String teamMode) {
        return PromptPolicy.buildSystemPrompt(
                role,
                persona,
                null,
                null,
                null,
                null,
                "temporary",
                "cn",
                teamMode
        );
    }

    private static DeepAgentSpec deepAgentSpec(int maxIterations) {
        DeepAgentSpec spec = new DeepAgentSpec();
        spec.setMaxIterations(maxIterations);
        return spec;
    }

    private static TeamAgentSpec specWithAgents(Map<String, ? extends AgentConfigurator.DeepAgentSpec> agents) {
        TeamAgentSpec spec = new TeamAgentSpec();
        Map<String, AgentConfigurator.DeepAgentSpec> copied = new LinkedHashMap<>();
        copied.putAll(agents);
        spec.setAgents(copied);
        spec.setLeader(new LeaderSpec());
        return spec;
    }

    private static AgentConfigurator.DeepAgentSpec resolveAgentSpec(
            TeamAgentSpec spec,
            AgentConfigurator.TeamRole role,
            String memberName) {
        TeamAgent agent = new TeamAgent(new AgentConfigurator.AgentCard("test", "test", "test"));
        return agent.resolveAgentSpec(spec, role, memberName);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    /**
     * Per-test backend/database fixture.
     *
     * <p>Mirrors Python's {@code db}, {@code message_bus}, and TeamBackend fixtures in
     * {@code tests/unit_tests/agent_teams/test_predefined_team.py}.</p>
     */
    private record Fixture(InMemoryTeamDatabase database, TeamBackend backend) {
    }

    /**
     * Recording messager collaborator used by predefined team backend tests.
     *
     * <p>Mirrors Python's {@code AsyncMock(spec=Messager)} fixture in
     * {@code tests/unit_tests/agent_teams/test_predefined_team.py}.</p>
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
