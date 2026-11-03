
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Tag("agent-teams-team-rail-slice")
class TeamRailCompatibilityTest {

    @AfterEach
    void resetSpawnSession() {
        // setSessionId pins a new session id into SpawnContext's
        // InheritableThreadLocal and never restores the previous value.
        // Without this reset, the leaked id poisons later test classes whose
        // TeamBackend constructs from SpawnContext.getSessionId() and then
        // publishes on a session-prefixed topic the subscriber never matches.
        SpawnContext.setSessionId("");
    }

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
                "view_task", "plan_mode", "submit_plan").doesNotContain("write_plan");
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
    void teamInfoSectionShouldRenderWorkspaceOnlyLikePythonBuilder() {
        var section = TeamRail.buildTeamInfoSection(null, ".team/solo/", null, "en");

        assertThat(section).isNotNull();
        assertThat(section.getName()).isEqualTo(TeamRail.INFO);
        assertThat(section.getPriority()).isEqualTo(65);
        assertThat(section.render("en")).contains("# Team Info", "Team Shared Workspace", "`.team/solo/`");
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
                .updateState(Map.of(InterruptConstants.INTERRUPTION_KEY, interruptionState("resume")));
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

    private static ToolInterruptionState interruptionState(String callId) {
        ToolInterruptEntry entry = new ToolInterruptEntry();
        ToolCall toolCall = new ToolCall();
        toolCall.setId(callId);
        toolCall.setName("tool");
        entry.setToolCall(toolCall);
        ToolInterruptionState state = new ToolInterruptionState();
        state.setInterruptedTools(Map.of(callId, entry));
        return state;
    }
}
