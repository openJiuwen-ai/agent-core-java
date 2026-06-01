package com.openjiuwen.agent_teams.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.policy}.
 */
class AgentPolicyTest {

    @Test
    void rolePolicyLoadsLeaderTemplateWithoutStubText() {
        String policy = AgentPolicy.rolePolicy(AgentPolicy.TeamRole.LEADER, "en");

        assertTrue(policy.contains("You are TeamLeader"));
        assertTrue(policy.contains("create_task"));
        assertFalse(policy.contains("Place" + "holder"));
    }

    @Test
    void rolePolicyLoadsTeammateTemplateWithoutStubText() {
        String policy = AgentPolicy.rolePolicy(AgentPolicy.TeamRole.MEMBER, "en");

        assertTrue(policy.contains("You are Teammate"));
        assertTrue(policy.contains("claim_task"));
        assertFalse(policy.contains("Place" + "holder"));
    }

    @Test
    void formatTeamMembersExcludesSelfAndUsesPythonLineFormat() {
        String section = AgentPolicy.formatTeamMembers(List.of(
                Map.of("member_name", "leader", "display_name", "Leader", "desc", "owns direction"),
                Map.of("member_name", "dev", "display_name", "Developer", "desc", "builds")
        ), "en", "leader");

        assertTrue(section.contains("## Relationships"));
        assertFalse(section.contains("member_name=leader"));
        assertTrue(section.contains("- member_name=dev display_name=Developer :: builds"));
    }

    @Test
    void formatTeamInfoFallsBackToChineseLabelsForUnknownLanguage() {
        String section = AgentPolicy.formatTeamInfo(Map.of(
                "team_name", "team-a",
                "display_name", "Team A",
                "desc", "finish the work"
        ), "ja");

        assertTrue(section.contains("## 团队信息"));
        assertTrue(section.contains("团队名"));
        assertTrue(section.contains("team-a"));
    }

    @Test
    void buildSystemPromptComposesLeaderPolicyWorkflowLifecycleAndContext() {
        String prompt = AgentPolicy.buildSystemPrompt(
                AgentPolicy.TeamRole.LEADER,
                "senior architect",
                "Base instruction.",
                Map.of("team_name", "alpha", "display_name", "Alpha Team", "desc", "deliver safely"),
                List.of(
                        Map.of("member_name", "leader", "display_name", "Leader", "desc", "coordinates"),
                        Map.of("member_name", "qa", "display_name", "QA", "desc", "verifies")
                ),
                "leader",
                "persistent",
                "en",
                "predefined"
        );

        assertTrue(prompt.contains("Your member_name: leader"));
        assertTrue(prompt.contains("You are TeamLeader"));
        assertTrue(prompt.contains("Predefined Team Mode"));
        assertTrue(prompt.contains("Persistent"));
        assertTrue(prompt.contains("Current Persona: senior architect"));
        assertTrue(prompt.contains("team_name: alpha"));
        assertTrue(prompt.contains("member_name=qa display_name=QA :: verifies"));
        assertTrue(prompt.contains("Base instruction."));
        assertFalse(prompt.contains("{{"));
        assertFalse(prompt.contains("Place" + "holder"));
    }

    @Test
    void buildSystemPromptForMemberSkipsLeaderOnlySections() {
        String prompt = AgentPolicy.buildSystemPrompt(
                AgentPolicy.TeamRole.MEMBER,
                "backend specialist",
                null,
                null,
                null,
                "dev",
                "temporary",
                "en",
                "default"
        );

        assertTrue(prompt.contains("Your member_name: dev"));
        assertTrue(prompt.contains("You are Teammate"));
        assertTrue(prompt.contains("Current Persona: backend specialist"));
        assertFalse(prompt.contains("Workflow ("));
        assertFalse(prompt.contains("Lifecycle"));
        assertFalse(prompt.contains("Place" + "holder"));
    }
}
