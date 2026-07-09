
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.team.TeamRole;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Tag("agent-teams-policy-slice")
class PolicyCompatibilityTest {
    @Test
    void leaderPolicyShouldMentionKeyResponsibilitiesLikePythonPolicy() {
        String policy = AgentTeamPolicy.rolePolicy(TeamRole.LEADER);

        assertThat(policy).contains("DAG", "create_task");
    }

    @Test
    void teammatePolicyShouldMentionTaskWorkflowLikePythonPolicy() {
        String policy = AgentTeamPolicy.rolePolicy(TeamRole.MEMBER);

        assertThat(policy).contains("view_task");
    }

    @Test
    void buildSystemPromptShouldIncludeAllPartsLikePythonPolicy() {
        String prompt = AgentTeamPolicy.buildSystemPrompt(TeamRole.LEADER, "PM Expert");

        assertThat(prompt).contains("PM Expert", "create_task");
    }

    @Test
    void buildSystemPromptShouldFormatTeamContextAndExcludeSelfMember() {
        String prompt = AgentTeamPolicy.builder(TeamRole.LEADER, "Architect").language("en").memberName("leader")
                .teamInfo(Map.of("team_name", "core-team", "display_name", "Core Team", "desc", "Ship parity"))
                .teamMembers(List.of(Map.of("member_name", "leader", "display_name", "Lead", "desc", "Self"),
                        Map.of("member_name", "worker", "display_name", "Worker", "desc", "Implementation")))
                .basePrompt("Extra instruction").lifecycle("persistent").teamMode("predefined").build();

        assertThat(prompt).contains("Your member_name: leader").contains("Team Info").contains("team_name: core-team")
                .contains("display_name: Core Team").contains("Team Goal & Directives: Ship parity")
                .contains("member_name=worker display_name=Worker :: Implementation")
                .contains("Workflow (Predefined Team Mode)").contains("Persistent Team").contains("Extra instruction")
                .doesNotContain("member_name=leader display_name=Lead");
    }
}
