/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.rails.FirstIterationGate;
import com.openjiuwen.agent_teams.rails.TeamPolicyRail;
import com.openjiuwen.agent_teams.rails.TeamToolApprovalRail;
import com.openjiuwen.agent_teams.rails.TeamToolRail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Missing-test parity coverage for human-agent setup role, spawn payload, and rails.
 *
 * <p>Mirrors Python's {@code test_human_agent_setup} in
 * {@code tests/unit_tests/agent_teams/agent/test_human_agent_setup.py}.</p>
 */
class HumanAgentSetupPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/agent/test_human_agent_setup.py";
    private static final String HUMAN_MEMBER = "human_agent";
    private static final String LEADER_MEMBER = "team_leader";
    private static final String TEAM_NAME = "hitt_team";

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonHumanAgentSetupTests(String pythonNodeId, Scenario scenario) {
        scenario.run();
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("test_predefined_human_member_carries_human_agent_role",
                        HumanAgentSetupPythonParityTest::predefinedHumanMemberCarriesHumanAgentRole),
                arg("test_human_agent_spawn_payload_marks_role",
                        HumanAgentSetupPythonParityTest::humanAgentSpawnPayloadMarksRole),
                arg("test_human_agent_role_restored_from_member_row",
                        HumanAgentSetupPythonParityTest::humanAgentRoleRestoredFromMemberRow),
                arg("test_teammate_role_inferred_from_backend",
                        HumanAgentSetupPythonParityTest::teammateRoleInferredFromBackend),
                arg("test_human_agent_skips_first_iteration_gate",
                        HumanAgentSetupPythonParityTest::humanAgentSkipsFirstIterationGate),
                arg("test_human_agent_attaches_team_tool_and_policy_rails",
                        HumanAgentSetupPythonParityTest::humanAgentAttachesTeamToolAndPolicyRails),
                arg("test_human_agent_never_attaches_tool_approval_rail",
                        HumanAgentSetupPythonParityTest::humanAgentNeverAttachesToolApprovalRail)
        );
    }

    private static Arguments arg(String name, Scenario scenario) {
        return Arguments.of(SOURCE + "::" + name, scenario);
    }

    private static void predefinedHumanMemberCarriesHumanAgentRole() {
        TeamAgent leader = humanAgentTeam();

        TeamMemberSpec member = humanMember(leader.getSpec());
        TeamRuntimeContext context = leader.buildMemberContext(member);

        assertThat(context.getRole()).isEqualTo(TeamRole.HUMAN_AGENT);
    }

    private static void humanAgentSpawnPayloadMarksRole() {
        TeamAgent leader = humanAgentTeam();
        TeamRuntimeContext context = leader.buildMemberContext(humanMember(leader.getSpec()));

        Map<String, Object> payload = leader.buildSpawnPayload(context, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> coordination = (Map<String, Object>) payload.get("coordination");
        assertThat(coordination).containsEntry("role", TeamRole.HUMAN_AGENT.value());
        assertThat(coordination).containsEntry("member_name", HUMAN_MEMBER);
    }

    private static void humanAgentRoleRestoredFromMemberRow() {
        TeamRuntimeContext context = contextFromPersistedRow(
                snapshot("human_alice", TeamRole.HUMAN_AGENT, "user avatar", null, null),
                "human_alice"
        );

        assertThat(context).isNotNull();
        assertThat(context.getRole()).isEqualTo(TeamRole.HUMAN_AGENT);
        assertThat(context.getMemberName()).isEqualTo("human_alice");
        assertThat(context.getPersona()).isEqualTo("user avatar");
    }

    private static void teammateRoleInferredFromBackend() {
        TeamRuntimeContext context = contextFromPersistedRow(
                snapshot("dev-1", TeamRole.TEAMMATE, "backend dev", null, null),
                "dev-1"
        );

        assertThat(context).isNotNull();
        assertThat(context.getRole()).isEqualTo(TeamRole.TEAMMATE);
        assertThat(context.getMemberName()).isEqualTo("dev-1");
    }

    private static void humanAgentSkipsFirstIterationGate() {
        TeamAgent avatar = buildHumanAgentRuntime(humanAgentSpec());

        assertThat(avatar.getResources().getFirstIterGate()).isNull();
        assertThat(avatar.getHarness().findRails(AgentConfigurator.FirstIterationGateHandle.class)).isEmpty();
        assertThat(avatar.getHarness().findRails(FirstIterationGate.class)).isEmpty();
    }

    private static void humanAgentAttachesTeamToolAndPolicyRails() {
        TeamAgent avatar = buildHumanAgentRuntime(humanAgentSpec());

        assertThat(avatar.getHarness().findRails(TeamToolRail.class)).hasSize(1);
        assertThat(avatar.getHarness().findRails(TeamPolicyRail.class)).hasSize(1);
    }

    private static void humanAgentNeverAttachesToolApprovalRail() {
        TeamAgentSpec spec = humanAgentSpec();
        spec.getAgents().get("leader").setApprovalRequiredTools(List.of("write_file"));

        TeamAgent avatar = buildHumanAgentRuntime(spec);

        assertThat(avatar.getHarness().findRails(TeamToolApprovalRail.class)).isEmpty();
    }

    private static TeamRuntimeContext contextFromPersistedRow(TeamMember.MemberSnapshot row, String memberName) {
        TeamAgent leader = humanAgentTeam();
        RecordingMemberStore store = new RecordingMemberStore(row);
        ConfiguredTeamBackend backend = new ConfiguredTeamBackend(
                TEAM_NAME,
                LEADER_MEMBER,
                true,
                Map.of(),
                null,
                "build_mode",
                humanAgentSpec().getPredefinedMembers(),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                LEADER_MEMBER,
                store
        );
        leader.getInfra().setTeamBackend(backend);

        return leader.getSpawnManager().buildContextFromDb(memberName).toCompletableFuture().join();
    }

    private static TeamAgent buildHumanAgentRuntime(TeamAgentSpec spec) {
        TeamAgent leader = buildLeader(spec);
        TeamMemberSpec member = humanMember(spec);
        TeamRuntimeContext context = leader.buildMemberContext(member);

        AgentCard card = new AgentCard(
                TEAM_NAME + "_" + member.getMemberName(),
                member.getMemberName(),
                member.getPersona()
        );
        TeamAgent avatar = new TeamAgent(card);
        avatar.configure(spec, context);
        return avatar;
    }

    private static TeamAgent humanAgentTeam() {
        return buildLeader(humanAgentSpec());
    }

    private static TeamAgent buildLeader(TeamAgentSpec spec) {
        TeamAgent leader = new TeamAgent(new AgentCard(TEAM_NAME + "_" + LEADER_MEMBER, LEADER_MEMBER, "Leader"));
        leader.configure(spec, leaderContext());
        return leader;
    }

    private static TeamAgentSpec humanAgentSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(TEAM_NAME);
        spec.setEnableHitt(true);
        spec.setAgents(Map.of(
                "leader", new DeepAgentSpec(),
                "teammate", new DeepAgentSpec()
        ));
        spec.setPredefinedMembers(List.of(member(
                HUMAN_MEMBER,
                "Human",
                TeamRole.HUMAN_AGENT,
                "Default human collaborator"
        )));
        return spec;
    }

    private static TeamRuntimeContext leaderContext() {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(TeamRole.LEADER);
        context.setMemberName(LEADER_MEMBER);
        context.setPersona("leader");
        context.setTeamSpec(new TeamSpec(TEAM_NAME, "HITT", LEADER_MEMBER));
        context.setDbConfig(Map.of());
        return context;
    }

    private static TeamMemberSpec humanMember(TeamAgentSpec spec) {
        return spec.getPredefinedMembers().stream()
                .filter(member -> TeamRole.HUMAN_AGENT == member.getRoleType())
                .findFirst()
                .orElseThrow();
    }

    private static TeamMemberSpec member(String name, String displayName, TeamRole role, String persona) {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName(name);
        member.setDisplayName(displayName);
        member.setRoleType(role);
        member.setPersona(persona);
        return member;
    }

    private static TeamMember.MemberSnapshot snapshot(
            String memberName,
            TeamRole role,
            String desc,
            String prompt,
            String modelRefJson
    ) {
        return new TeamMember.MemberSnapshot(
                "unstarted",
                "idle",
                memberName,
                role.value(),
                desc,
                prompt,
                modelRefJson
        );
    }

    @FunctionalInterface
    private interface Scenario {
        void run();
    }

    private static final class RecordingMemberStore implements TeamMember.MemberStore {
        private final Map<String, TeamMember.MemberSnapshot> rows = new LinkedHashMap<>();

        private RecordingMemberStore(TeamMember.MemberSnapshot row) {
            rows.put(row.memberName(), row);
        }

        @Override
        public CompletionStage<TeamMember.MemberSnapshot> getMember(String memberName, String teamName) {
            return CompletableFuture.completedFuture(rows.get(memberName));
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
