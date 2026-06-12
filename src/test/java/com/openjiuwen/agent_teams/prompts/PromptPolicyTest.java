/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests role-aware prompt policy assembly.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/prompts/policy.py}.</p>
 */
class PromptPolicyTest {

    @BeforeEach
    void clearCache() {
        PromptLoader.clearCacheForTests();
    }

    @Test
    void rolePolicyLoadsLeaderOrTeammateTemplate() {
        assertThat(PromptPolicy.rolePolicy(TeamRole.LEADER, "en"))
                .isEqualTo(PromptLoader.loadTemplate("leader_policy", "en").getContent());
        assertThat(PromptPolicy.rolePolicy(TeamRole.TEAMMATE, "en"))
                .isEqualTo(PromptLoader.loadTemplate("teammate_policy", "en").getContent());
        assertThat(PromptPolicy.rolePolicy(TeamRole.HUMAN_AGENT, "en"))
                .isEqualTo(PromptLoader.loadTemplate("teammate_policy", "en").getContent());
    }

    @Test
    void leaderPromptIncludesWorkflowLifecycleTeamContextAndBasePrompt() {
        String prompt = PromptPolicy.buildSystemPrompt(
                TeamRole.LEADER,
                "Lead carefully",
                "Extra base instruction.",
                Map.of("team_name", "alpha", "display_name", "Alpha Team", "desc", "Ship the plan"),
                List.of(
                        Map.of("member_name", "leader", "display_name", "Leader", "desc", "self"),
                        Map.of("member_name", "worker", "display_name", "Worker", "desc", "does work")
                ),
                "leader",
                "persistent",
                "en",
                "predefined"
        );

        assertThat(prompt).contains("Your member_name: leader");
        assertThat(prompt).contains("Current Persona: Lead carefully");
        assertThat(prompt).contains("## Team Info");
        assertThat(prompt).contains("- team_name: alpha");
        assertThat(prompt).contains("- display_name: Alpha Team");
        assertThat(prompt).contains("- Team Goal & Directives: Ship the plan");
        assertThat(prompt).contains("## Relationships");
        assertThat(prompt).contains("member_name=worker display_name=Worker :: does work");
        assertThat(prompt).doesNotContain("member_name=leader display_name=Leader :: self");
        assertThat(prompt).contains("Extra base instruction.");
        assertThat(prompt).contains((String) PromptLoader.loadTemplate("leader_policy", "en").getContent());
        assertThat(prompt).contains((String) PromptLoader.loadTemplate("leader_workflow_predefined", "en").getContent());
        assertThat(prompt).contains((String) PromptLoader.loadTemplate("lifecycle_persistent", "en").getContent());
    }

    @Test
    void teammatePromptOmitsLeaderOnlySections() {
        String prompt = PromptPolicy.buildSystemPrompt(
                TeamRole.TEAMMATE,
                "Implement tasks",
                null,
                null,
                null,
                "worker",
                "persistent",
                "en",
                "hybrid"
        );

        assertThat(prompt).contains("Your member_name: worker");
        assertThat(prompt).contains("Current Persona: Implement tasks");
        assertThat(prompt).contains((String) PromptLoader.loadTemplate("teammate_policy", "en").getContent());
        assertThat(prompt).doesNotContain((String) PromptLoader.loadTemplate("leader_workflow_hybrid", "en").getContent());
        assertThat(prompt).doesNotContain((String) PromptLoader.loadTemplate("lifecycle_persistent", "en").getContent());
    }

    @Test
    void unknownTeamModeFallsBackToDefaultWorkflow() {
        String prompt = PromptPolicy.buildSystemPrompt(
                TeamRole.LEADER,
                "Lead",
                null,
                null,
                null,
                null,
                "temporary",
                "en",
                "unknown"
        );

        assertThat(prompt).contains((String) PromptLoader.loadTemplate("leader_workflow", "en").getContent());
        assertThat(prompt).contains((String) PromptLoader.loadTemplate("lifecycle_temporary", "en").getContent());
    }
}
