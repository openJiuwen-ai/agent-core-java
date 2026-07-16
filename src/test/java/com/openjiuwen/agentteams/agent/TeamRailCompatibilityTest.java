
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Tag("agent-teams-team-rail-slice")
class TeamRailCompatibilityTest {
    @Test
    void roleSectionShouldMatchPythonTeamRailLeaderPolicyShape() {
        var section = TeamRail.buildTeamRoleSection(TeamRole.LEADER, "leader1", "build_mode", "cn");

        String content = section.render("cn");

        assertThat(section.getName()).isEqualTo(TeamRail.ROLE);
        assertThat(section.getPriority()).isEqualTo(11);
        assertThat(content).contains("# 团队角色", "你的 member_name: leader1", "create_task");
    }

    @Test
    void hittSectionShouldFollowPythonRoleSpecificHumanAgentPromptShape() {
        var leaderSection =
            TeamRail.buildTeamHittSection(TeamRole.LEADER, List.of("human_pm", "human_designer"), "cn", "lead");
        var humanSection = TeamRail.buildTeamHittSection(TeamRole.HUMAN_AGENT, List.of("human_pm", "human_designer"),
                "en", "human_pm");

        assertThat(leaderSection.getName()).isEqualTo(TeamRail.HITT);
        assertThat(leaderSection.getPriority()).isEqualTo(12);
        assertThat(leaderSection.render("cn")).contains("HITT", "`human_designer`, `human_pm`", "send_message",
                "shutdown_member");
        assertThat(humanSection.render("en")).contains("You are a human member", "Your member_name is `human_pm`",
                "send_message");
    }

    @Test
    void teammateShouldOnlyReceiveRolePersonaAndExtraStaticSections() {
        TeamRail rail = new TeamRail(TeamRole.MEMBER, "Backend specialist", "dev1", "temporary", "plan_mode", "en",
                "default", "Be precise", null, null, List.of());

        assertThat(rail.getStaticSections()).extracting(section -> section.getName()).containsExactly(TeamRail.ROLE,
                TeamRail.PERSONA, TeamRail.EXTRA);
        assertThat(rail.getStaticSections().get(0).render("en")).contains("# Team Role", "Your member_name: dev1",
                "view_task", "plan_mode");
    }

    @Test
    void teamAgentShouldRegisterTeamRailAndInjectSectionsBeforeModelCallLikePythonTeamAgent() {
        TeamAgent agent = new TeamAgent().configure(
                TeamAgentSpec.builder().name("delivery").language("cn").lifecycle("persistent").humanAgentEnabled(true)
                        .members(List.of(
                                TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).description("PM Expert")
                                        .build(),
                                TeamMemberSpec.builder().name("human_pm").role(TeamRole.HUMAN_AGENT).build()))
                        .build(),
                TeamRuntimeContext.builder().teamId("delivery").memberName("lead").role(TeamRole.LEADER)
                        .metadata(Map.of("persona", "PM Expert", "teamworkspace_mount", ".team/delivery/",
                                "teamworkspace_path", "./team-workspace"))
                        .build());

        assertThat(agent.getDeepAgent().getConfig().getSystemPrompt()).isEmpty();
        TeamRail rail = (TeamRail) agent.getDeepAgent().getRegisteredRails().stream().filter(TeamRail.class::isInstance)
                .findFirst().orElseThrow();

        rail.beforeModelCall(AgentCallbackContext.builder().build());

        var builder = agent.getDeepAgent().getAgent().getPromptBuilder();
        assertThat(builder.hasSection(TeamRail.ROLE)).isTrue();
        assertThat(builder.hasSection(TeamRail.WORKFLOW)).isTrue();
        assertThat(builder.hasSection(TeamRail.LIFECYCLE)).isTrue();
        assertThat(builder.hasSection(TeamRail.PERSONA)).isTrue();
        assertThat(builder.hasSection(TeamRail.HITT)).isTrue();
        assertThat(builder.hasSection(TeamRail.INFO)).isTrue();
        assertThat(builder.hasSection(TeamRail.MEMBERS)).isTrue();
        assertThat(builder.getSection(TeamRail.ROLE).render("cn")).contains("TeamLeader", "create_task");
        assertThat(builder.getSection(TeamRail.HITT).render("cn")).contains("human_pm", "send_message");
        assertThat(builder.getSection(TeamRail.PERSONA).render("cn")).contains("PM Expert");
        assertThat(builder.getSection(TeamRail.INFO).render("cn")).contains("# 团队信息", "delivery");
        assertThat(builder.getSection(TeamRail.INFO).render("cn")).contains("`.team/delivery/`", "`./team-workspace`");
        assertThat(builder.getSection(TeamRail.MEMBERS).render("cn")).contains("# 成员关系", "human_pm")
                .doesNotContain("member_name=lead");
    }

    @Test
    void teamRailShouldRefreshDynamicMembersWhenBackendMtimeChangesLikePythonCache() {
        TeamAgent agent = new TeamAgent().configure(
                TeamAgentSpec.builder().name("dynamic-rail").language("en").members(List.of(
                        TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).description("Leader").build(),
                        TeamMemberSpec.builder().name("dev1").role(TeamRole.MEMBER).description("Coder").build()))
                        .build(),
                TeamRuntimeContext.builder().teamId("dynamic-rail").memberName("lead").role(TeamRole.LEADER).metadata(
                        Map.of("teamworkspace_mount", ".team/dynamic-rail/", "teamworkspace_path", "./team-workspace"))
                        .build());
        TeamRail rail = (TeamRail) agent.getDeepAgent().getRegisteredRails().stream().filter(TeamRail.class::isInstance)
                .findFirst().orElseThrow();

        rail.beforeModelCall(AgentCallbackContext.builder().build());
        var builder = agent.getDeepAgent().getAgent().getPromptBuilder();
        String firstMembers = builder.getSection(TeamRail.MEMBERS).render("en");
        assertThat(firstMembers).contains("# Relationships", "member_name=dev1", "Coder").doesNotContain("Newbie")
                .doesNotContain("member_name=lead");

        agent.getTeamBackend().spawnMember("dev2", "Newbie", null).join();
        rail.beforeModelCall(AgentCallbackContext.builder().build());

        assertThat(builder.getSection(TeamRail.MEMBERS).render("en"))
                .contains("member_name=dev1", "member_name=dev2", "Newbie").doesNotContain("member_name=lead");
    }

    @Test
    void teamBackendListMembersShouldExcludeCurrentMemberLikePythonTeamBackend() {
        TeamAgent agent = new TeamAgent().configure(
                TeamAgentSpec.builder().name("roster-team").language("en").members(List.of(
                        TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).description("Leader").build(),
                        TeamMemberSpec.builder().name("dev1").role(TeamRole.MEMBER).description("Coder").build()))
                        .build(),
                TeamRuntimeContext.builder().teamId("roster-team").memberName("lead").role(TeamRole.LEADER).metadata(
                        Map.of("teamworkspace_mount", ".team/roster-team/", "teamworkspace_path", "./team-workspace"))
                        .build());

        assertThat(agent.getTeamBackend().listMembers()).extracting(member -> member.getMemberName())
                .containsExactly("dev1");
    }

    @Test
    void teamRailShouldKeepInfoAndMembersSectionsAfterIndependentProbeChanges() {
        TeamAgent agent = new TeamAgent().configure(
                TeamAgentSpec.builder().name("probe-team").language("en").members(List.of(
                        TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).description("Leader").build(),
                        TeamMemberSpec.builder().name("dev1").role(TeamRole.MEMBER).description("Coder").build()))
                        .build(),
                TeamRuntimeContext.builder().teamId("probe-team").memberName("lead").role(TeamRole.LEADER).metadata(
                        Map.of("teamworkspace_mount", ".team/probe-team/", "teamworkspace_path", "./team-workspace"))
                        .build());
        TeamRail rail = (TeamRail) agent.getDeepAgent().getRegisteredRails().stream().filter(TeamRail.class::isInstance)
                .findFirst().orElseThrow();

        rail.beforeModelCall(AgentCallbackContext.builder().build());
        String initialInfo = agent.getDeepAgent().getAgent().getPromptBuilder().getSection(TeamRail.INFO).render("en");
        String initialMembers =
            agent.getDeepAgent().getAgent().getPromptBuilder().getSection(TeamRail.MEMBERS).render("en");

        agent.getTeamBackend().spawnMember("dev2", "Newbie", null).join();
        rail.beforeModelCall(AgentCallbackContext.builder().build());

        String refreshedInfo =
            agent.getDeepAgent().getAgent().getPromptBuilder().getSection(TeamRail.INFO).render("en");
        String refreshedMembers =
            agent.getDeepAgent().getAgent().getPromptBuilder().getSection(TeamRail.MEMBERS).render("en");

        assertThat(initialInfo).contains("`.team/probe-team/`", "`./team-workspace`");
        assertThat(refreshedInfo).contains("`.team/probe-team/`", "`./team-workspace`");
        assertThat(initialMembers).contains("member_name=dev1").doesNotContain("member_name=lead");
        assertThat(refreshedMembers).contains("member_name=dev1", "member_name=dev2")
                .doesNotContain("member_name=lead");
    }

    @Test
    void teamInfoSectionShouldRenderWorkspaceOnlyLikePythonBuilder() {
        var section = TeamRail.buildTeamInfoSection(null, ".team/solo/", null, "en");

        assertThat(section).isNotNull();
        assertThat(section.getName()).isEqualTo(TeamRail.INFO);
        assertThat(section.getPriority()).isEqualTo(65);
        assertThat(section.render("en")).contains("# Team Info", "Team Shared Workspace", "`.team/solo/`");
    }

    @Test
    void teamRailUninitShouldRemoveStaticAndDynamicSectionsLikePython() {
        TeamAgent agent = new TeamAgent().configure(
                TeamAgentSpec.builder().name("uninit-rail").language("en").members(List.of(
                        TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).description("Leader").build(),
                        TeamMemberSpec.builder().name("dev1").role(TeamRole.MEMBER).description("Coder").build()))
                        .build(),
                TeamRuntimeContext.builder().teamId("uninit-rail").memberName("lead").role(TeamRole.LEADER)
                        .metadata(Map.of("persona", "PM")).build());
        TeamRail rail = (TeamRail) agent.getDeepAgent().getRegisteredRails().stream().filter(TeamRail.class::isInstance)
                .findFirst().orElseThrow();

        rail.beforeModelCall(AgentCallbackContext.builder().build());
        var builder = agent.getDeepAgent().getAgent().getPromptBuilder();
        assertThat(builder.hasSection(TeamRail.ROLE)).isTrue();
        assertThat(builder.hasSection(TeamRail.INFO)).isTrue();
        assertThat(builder.hasSection(TeamRail.MEMBERS)).isTrue();

        rail.uninit(agent.getDeepAgent());

        assertThat(builder.hasSection(TeamRail.ROLE)).isFalse();
        assertThat(builder.hasSection(TeamRail.WORKFLOW)).isFalse();
        assertThat(builder.hasSection(TeamRail.LIFECYCLE)).isFalse();
        assertThat(builder.hasSection(TeamRail.PERSONA)).isFalse();
        assertThat(builder.hasSection(TeamRail.INFO)).isFalse();
        assertThat(builder.hasSection(TeamRail.MEMBERS)).isFalse();
    }

    @Test
    void teamAgentShouldExposeSessionAndEventListenerStateLikePython() {
        TeamAgent agent =
            new TeamAgent()
                    .configure(
                            TeamAgentSpec.builder().name("state-team")
                                    .members(List
                                            .of(TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).build()))
                                    .build(),
                            TeamRuntimeContext.builder().teamId("state-team").memberName("lead").role(TeamRole.LEADER)
                                    .build());

        Object listener = new Object();
        agent.addEventListener(listener);
        agent.setSessionId("sess-1");
        agent.deliverInput("please review");
        agent.persistAllocatorState();
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.setUserInputs(Map.of("resume", "ok"));
        agent.getAgentSession()
                .updateState(Map.of(ToolInterruptionState.INTERRUPTION_KEY,
                        ToolInterruptionState.builder()
                                .interruptedTools(List.of(ToolInterruptEntry.builder()
                                        .request(InterruptRequest.builder().interruptId("resume").build()).build()))
                                .build()));
        agent.setInFlightRound(true);
        agent.resumeInterrupt(resumeInput);
        agent.removeEventListener(listener);

        assertThat(agent.sessionId()).isEqualTo("sess-1");
        assertThat(agent.pendingUserQuery()).isEqualTo("please review");
        assertThat(agent.eventListeners()).isEmpty();
        assertThat(agent.getContext().getMetadata()).containsEntry("session_id", "sess-1");
        assertThat(agent.getStreamController().getPendingInterruptResumes()).containsExactly(resumeInput);
        assertThat(agent.getContext().getMetadata()).containsEntry("pending_interrupt_resume_count", 1);
    }
}
