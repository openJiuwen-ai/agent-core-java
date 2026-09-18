/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.interaction.HumanAgentMessage;
import com.openjiuwen.agent_teams.interaction.InteractPayload;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections.TeamSectionName;
import com.openjiuwen.agent_teams.rails.TeamPlanModeRail;
import com.openjiuwen.agent_teams.rails.TeamPolicyRail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Missing-test parity coverage for TeamAgent configuration and initial routing.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_agent} in
 * {@code tests/unit_tests/agent_teams/test_team_agent.py}.</p>
 */
class TeamAgentPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/test_team_agent.py";

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonTeamAgentTests(String pythonNodeId, Scenario scenario) throws Exception {
        scenario.run();
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("test_team_agent_leader_policy", TeamAgentPythonParityTest::teamAgentLeaderPolicy),
                arg("test_team_plan_leader_mounts_team_plan_mode_rail",
                        TeamAgentPythonParityTest::teamPlanLeaderMountsTeamPlanModeRail),
                arg("test_team_normal_leader_does_not_mount_team_plan_mode_rail",
                        TeamAgentPythonParityTest::teamNormalLeaderDoesNotMountTeamPlanModeRail),
                arg("test_spawn_payload_contains_member_identity",
                        TeamAgentPythonParityTest::spawnPayloadContainsMemberIdentity),
                arg("test_spawn_config_contains_serializable_team_agent_payload",
                        TeamAgentPythonParityTest::spawnConfigContainsSerializableTeamAgentPayload),
                arg("test_runtime_context_roundtrips_with_pydantic_serialization",
                        TeamAgentPythonParityTest::runtimeContextRoundtripsWithSerialization),
                arg("test_setup_agent_builds_leader_member_handle",
                        TeamAgentPythonParityTest::setupAgentBuildsLeaderMemberHandle),
                arg("test_leader_initial_direct_human_agent_route_skips_leader_user_input",
                        TeamAgentPythonParityTest::leaderInitialDirectHumanAgentRouteSkipsLeaderUserInput),
                arg("test_leader_initial_human_agent_avatar_route_skips_leader_user_input",
                        TeamAgentPythonParityTest::leaderInitialHumanAgentAvatarRouteSkipsLeaderUserInput),
                arg("test_leader_initial_direct_human_agent_stream_route_skips_leader_user_input",
                        TeamAgentPythonParityTest::leaderInitialDirectHumanAgentStreamRouteSkipsLeaderUserInput),
                arg("test_leader_initial_human_agent_avatar_stream_route_skips_leader_user_input",
                        TeamAgentPythonParityTest::leaderInitialHumanAgentAvatarStreamRouteSkipsLeaderUserInput),
                arg("test_teammate_initial_dollar_input_stays_regular_user_input",
                        TeamAgentPythonParityTest::teammateInitialDollarInputStaysRegularUserInput),
                arg("test_inprocess_teammate_initial_dollar_input_stays_regular_user_input",
                        TeamAgentPythonParityTest::inprocessTeammateInitialDollarInputStaysRegularUserInput),
                arg("test_inprocess_teammate_initial_dollar_stream_stays_regular_user_input",
                        TeamAgentPythonParityTest::inprocessTeammateInitialDollarStreamStaysRegularUserInput),
                arg("test_setup_agent_builds_teammate_member_handle",
                        TeamAgentPythonParityTest::setupAgentBuildsTeammateMemberHandle)
        );
    }

    private static Arguments arg(String name, Scenario scenario) {
        return Arguments.of(SOURCE + "::" + name, scenario);
    }

    private static void teamAgentLeaderPolicy() {
        TeamAgent leader = schemaSpec(false).build();

        TeamPolicyRail policyRail = onlyRail(leader, TeamPolicyRail.class);

        String roleSection = policyRail.getStaticSections().stream()
                .filter(section -> TeamSectionName.ROLE.equals(section.getName()))
                .findFirst()
                .orElseThrow()
                .render("cn");
        assertThat(roleSection).contains("TeamLeader");
    }

    private static void teamPlanLeaderMountsTeamPlanModeRail() {
        TeamAgent leader = schemaSpec(true).build();

        assertThat(leader.getHarness().findRails(TeamPlanModeRail.class)).hasSize(1);
    }

    private static void teamNormalLeaderDoesNotMountTeamPlanModeRail() {
        TeamAgent leader = schemaSpec(false).build();

        assertThat(leader.getHarness().findRails(TeamPlanModeRail.class)).isEmpty();
    }

    private static void spawnPayloadContainsMemberIdentity() {
        TeamAgent leader = directLeader(true);
        TeamRuntimeContext ctx = leader.buildMemberContext(member("fe-1", "Frontend Expert", TeamRole.TEAMMATE,
                "Pursues interaction quality"));

        Map<String, Object> payload = leader.buildSpawnPayload(ctx, "Review the design system impact.");

        @SuppressWarnings("unchecked")
        Map<String, Object> coordination = (Map<String, Object>) payload.get("coordination");
        @SuppressWarnings("unchecked")
        Map<String, Object> transport = (Map<String, Object>) coordination.get("transport");
        assertThat(coordination).containsEntry("role", "teammate");
        assertThat(coordination).containsEntry("persona", "Pursues interaction quality");
        assertThat(transport).containsEntry("node_id", "fe-1");
        assertThat(payload).containsEntry("query", "Review the design system impact.");
    }

    private static void spawnConfigContainsSerializableTeamAgentPayload() {
        TeamAgent leader = directLeader(true);
        TeamRuntimeContext ctx = leader.buildMemberContext(member("be-1", "Backend Expert", TeamRole.TEAMMATE,
                "Careful backend architect"));

        SpawnAgentConfig spawnConfig = leader.buildSpawnConfig(ctx);
        TeamAgent teammate = TeamAgent.fromSpawnPayload(spawnConfig.getPayload()).toCompletableFuture().join();

        assertThat(spawnConfig.getAgentKind().value()).isEqualTo("team_agent");
        assertThat(spawnConfig.getPayload()).containsKeys("spec", "context");
        assertThat(teammate.getRole()).isEqualTo(TeamRole.TEAMMATE);
        assertThat(teammate.getCard().getName()).isEqualTo("be-1");
        assertThat(teammate.getRuntimeContext().getMessagerConfig().getNodeId()).isEqualTo("be-1");
    }

    private static void runtimeContextRoundtripsWithSerialization() {
        TeamRuntimeContext context = context(TeamRole.LEADER, "leader-1", "pm");
        Map<String, Object> dumped = AgentConfigurator.SpawnPayloadBuilder.dumpRuntimeContext(context);

        TeamAgent parsed = TeamAgent.fromSpawnPayload(Map.of("spec", dumpSpec(), "context", dumped))
                .toCompletableFuture()
                .join();

        assertThat(parsed.getRuntimeContext().getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(parsed.getRuntimeContext().getMemberName()).isEqualTo("leader-1");
        assertThat(parsed.getRuntimeContext().getPersona()).isEqualTo("pm");
    }

    private static void setupAgentBuildsLeaderMemberHandle() {
        TeamAgent leader = schemaSpec(false).build();

        assertThat(leader.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(leader.getTeamMember()).isNotNull();
        assertThat(leader.getTeamMember().getMemberName()).isEqualTo(leader.getMemberName());
    }

    private static void leaderInitialDirectHumanAgentRouteSkipsLeaderUserInput() {
        RecordingTeamAgent leader = recordingLeader();

        Object result = leader.invoke(Map.of("query", "$human-counter-1 @ai-counter-1 count two"))
                .toCompletableFuture()
                .join();

        assertThat(result).isNull();
        assertThat(leader.dispatched).hasSize(1);
        assertThat(leader.dispatched.get(0)).containsExactly(new HumanAgentMessage(
                "count two",
                "human-counter-1",
                "ai-counter-1"
        ));
    }

    private static void leaderInitialHumanAgentAvatarRouteSkipsLeaderUserInput() {
        RecordingTeamAgent leader = recordingLeader();

        leader.invoke(Map.of("query", "$human-counter-1 count two")).toCompletableFuture().join();

        assertThat(leader.dispatched).hasSize(1);
        assertThat(leader.dispatched.get(0)).containsExactly(new HumanAgentMessage("count two", "human-counter-1"));
    }

    private static void leaderInitialDirectHumanAgentStreamRouteSkipsLeaderUserInput() {
        RecordingTeamAgent leader = recordingLeader();

        List<Object> chunks = leader.stream(Map.of("query", "$human-counter-1 @ai-counter-1 count two"))
                .toCompletableFuture()
                .join();

        assertThat(chunks).isEmpty();
        assertThat(leader.dispatched.get(0)).containsExactly(new HumanAgentMessage(
                "count two",
                "human-counter-1",
                "ai-counter-1"
        ));
    }

    private static void leaderInitialHumanAgentAvatarStreamRouteSkipsLeaderUserInput() {
        RecordingTeamAgent leader = recordingLeader();

        List<Object> chunks = leader.stream(Map.of("query", "$human-counter-1 count two"))
                .toCompletableFuture()
                .join();

        assertThat(chunks).isEmpty();
        assertThat(leader.dispatched.get(0)).containsExactly(new HumanAgentMessage("count two", "human-counter-1"));
    }

    private static void teammateInitialDollarInputStaysRegularUserInput() {
        RecordingTeamAgent teammate = recordingTeammate();
        Map<String, Object> inputs = Map.of("query", "$human-counter-1 @ai-counter-1 count two");

        teammate.invoke(inputs).toCompletableFuture().join();

        assertThat(teammate.dispatched).isEmpty();
        assertThat(teammate.getPendingUserQuery()).isEqualTo(inputs.get("query"));
    }

    private static void inprocessTeammateInitialDollarInputStaysRegularUserInput() {
        RecordingTeamAgent teammate = recordingTeammate();
        Map<String, Object> inputs = Map.of("query", "$human-counter-1 @ai-counter-1 count two");

        teammate.invoke(inputs).toCompletableFuture().join();

        assertThat(teammate.dispatched).isEmpty();
        assertThat(teammate.getPendingUserQuery()).isEqualTo(inputs.get("query"));
    }

    private static void inprocessTeammateInitialDollarStreamStaysRegularUserInput() {
        RecordingTeamAgent teammate = recordingTeammate();
        Map<String, Object> inputs = Map.of("query", "$human-counter-1 @ai-counter-1 count two");

        List<Object> chunks = teammate.stream(inputs).toCompletableFuture().join();

        assertThat(chunks).isEmpty();
        assertThat(teammate.dispatched).isEmpty();
        assertThat(teammate.getPendingUserQuery()).isEqualTo(inputs.get("query"));
    }

    private static void setupAgentBuildsTeammateMemberHandle() {
        TeamAgent leader = directLeader(true);
        SpawnAgentConfig spawnConfig = leader.buildSpawnConfig(leader.buildMemberContext(
                member("be-1", "Backend Expert", TeamRole.TEAMMATE, "Careful backend architect")));

        TeamAgent teammate = TeamAgent.fromSpawnPayload(spawnConfig.getPayload()).toCompletableFuture().join();

        assertThat(teammate.getRole()).isEqualTo(TeamRole.TEAMMATE);
        assertThat(teammate.getTeamMember()).isNotNull();
        assertThat(teammate.getTeamMember().getMemberName()).isEqualTo("be-1");
    }

    private static <T> T onlyRail(TeamAgent agent, Class<T> railType) {
        List<Object> rails = agent.getHarness().findRails(railType);
        assertThat(rails).hasSize(1);
        return railType.cast(rails.get(0));
    }

    private static com.openjiuwen.agent_teams.schema.TeamAgentSpec schemaSpec(boolean enableTeamPlan) {
        com.openjiuwen.agent_teams.schema.TeamAgentSpec spec =
                new com.openjiuwen.agent_teams.schema.TeamAgentSpec();
        spec.setTeamName("delivery");
        spec.setEnableTeamPlan(enableTeamPlan);
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        return spec;
    }

    private static TeamAgent directLeader(boolean withTransport) {
        TeamAgent agent = new TeamAgent(new AgentCard("delivery_leader", "leader", "Leader"));
        agent.configure(baseSpec(true), context(TeamRole.LEADER, "leader", "pm", withTransport));
        return agent;
    }

    private static RecordingTeamAgent recordingLeader() {
        RecordingTeamAgent agent = new RecordingTeamAgent(new AgentCard("delivery_leader", "leader", "Leader"));
        agent.configure(baseSpec(true), context(TeamRole.LEADER, "leader", "pm", true));
        return agent;
    }

    private static RecordingTeamAgent recordingTeammate() {
        RecordingTeamAgent agent = new RecordingTeamAgent(new AgentCard("delivery_ai", "ai-counter-1", "Teammate"));
        agent.configure(baseSpec(true), context(TeamRole.TEAMMATE, "ai-counter-1", "ai counter", false));
        return agent;
    }

    private static AgentConfigurator.TeamAgentSpec baseSpec(boolean enableHitt) {
        AgentConfigurator.TeamAgentSpec spec = new AgentConfigurator.TeamAgentSpec();
        spec.setTeamName("delivery");
        spec.setEnableHitt(enableHitt);
        spec.setAgents(Map.of("leader", new DeepAgentSpec(), "teammate", new DeepAgentSpec()));
        spec.setPredefinedMembers(List.of(
                member("human-counter-1", "Human Counter", TeamRole.HUMAN_AGENT, "human counter"),
                member("ai-counter-1", "AI Counter", TeamRole.TEAMMATE, "ai counter")
        ));
        return spec;
    }

    private static TeamRuntimeContext context(TeamRole role, String memberName, String persona) {
        return context(role, memberName, persona, false);
    }

    private static TeamRuntimeContext context(
            TeamRole role,
            String memberName,
            String persona,
            boolean withTransport
    ) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(role);
        context.setMemberName(memberName);
        context.setPersona(persona);
        context.setTeamSpec(new TeamSpec("delivery", "delivery", "leader"));
        context.setDbConfig(Map.of());
        if (withTransport) {
            MessagerTransportConfig transport = new MessagerTransportConfig();
            transport.setTeamName("delivery-team");
            transport.setNodeId("leader");
            transport.setDirectAddr("tcp://127.0.0.1:19001");
            transport.setPubsubPublishAddr("tcp://127.0.0.1:19100");
            transport.setPubsubSubscribeAddr("tcp://127.0.0.1:19101");
            context.setMessagerConfig(transport);
        }
        return context;
    }

    private static TeamMemberSpec member(String name, String displayName, TeamRole role, String persona) {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName(name);
        member.setDisplayName(displayName);
        member.setRoleType(role);
        member.setPersona(persona);
        return member;
    }

    private static Map<String, Object> dumpSpec() {
        AgentConfigurator.TeamAgentSpec spec = baseSpec(true);
        return AgentConfigurator.SpawnPayloadBuilder.dumpTeamAgentSpec(spec);
    }

    @FunctionalInterface
    private interface Scenario {
        void run() throws Exception;
    }

    private static final class RecordingTeamAgent extends TeamAgent {
        private final List<List<InteractPayload>> dispatched = new ArrayList<>();

        private RecordingTeamAgent(AgentCard card) {
            super(card);
        }

        @Override
        protected CompletionStage<Void> dispatchInitialLeaderRoute(List<InteractPayload> payloads) {
            dispatched.add(List.copyOf(payloads));
            getStreamController().closeStream();
            return CompletableFuture.completedFuture(null);
        }
    }
}
