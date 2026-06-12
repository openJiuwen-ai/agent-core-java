/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests team.plan plan-agent prompt specialization.
 *
 * <p>Mirrors Python's module behavior in
 * {@code openjiuwen/agent_teams/prompts/team_plan_agent.py}.</p>
 */
class TeamPlanAgentTest {

    @BeforeEach
    void clearCache() {
        PromptLoader.clearCacheForTests();
    }

    @Test
    void constantsLoadTeamPlanTemplatesAndDescriptions() {
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("cn")).contains("团队规划专家");
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("en")).contains("Team planning specialist");
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN)
                .isEqualTo(String.valueOf(PromptLoader.loadTemplate("team_plan_agent", "cn").getContent()).strip());
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN)
                .isEqualTo(String.valueOf(PromptLoader.loadTemplate("team_plan_agent", "en").getContent()).strip());
        assertThat(TeamPlanAgent.DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT)
                .containsEntry("cn", TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN)
                .containsEntry("en", TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void descriptionAndPromptFallbackToChineseForDirectHelper() {
        assertThat(TeamPlanAgent.teamPlanAgentDescription("missing"))
                .isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("cn"));
        assertThat(TeamPlanAgent.teamPlanAgentPrompt("missing"))
                .isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN);
    }

    @Test
    void applyPromptReplacesOnlyBuiltinPlanAgentPrompt() {
        AgentCard originalCard = new AgentCard("plan", "plan_agent", "old description");
        TeamPlanAgent.PlanSubAgentConfig config = new TeamPlanAgent.PlanSubAgentConfig(
                originalCard,
                TeamPlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.get("en")
        );
        List<Object> subagents = new ArrayList<>();
        subagents.add("not a config");
        subagents.add(config);

        boolean updated = TeamPlanAgent.applyTeamPlanAgentPrompt(subagents, "en");

        assertThat(updated).isTrue();
        assertThat(config.getSystemPrompt()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN);
        assertThat(config.getAgentCard()).isNotSameAs(originalCard);
        assertThat(config.getAgentCard().getId()).isEqualTo("plan");
        assertThat(config.getAgentCard().getName()).isEqualTo("plan_agent");
        assertThat(config.getAgentCard().getDescription()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("en"));
        assertThat(originalCard.getDescription()).isEqualTo("old description");
    }

    @Test
    void applyPromptLeavesCustomPromptUntouched() {
        TeamPlanAgent.PlanSubAgentConfig config = new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard("plan", "plan_agent", "custom"),
                "custom prompt"
        );

        assertThat(TeamPlanAgent.applyTeamPlanAgentPrompt(List.of(config), "cn")).isFalse();
        assertThat(config.getSystemPrompt()).isEqualTo("custom prompt");
        assertThat(config.getAgentCard().getDescription()).isEqualTo("custom");
    }

    @Test
    void applyPromptSkipsMissingOrNonPlanAgents() {
        assertThat(TeamPlanAgent.applyTeamPlanAgentPrompt(null, "cn")).isFalse();
        assertThat(TeamPlanAgent.applyTeamPlanAgentPrompt(List.of(), "cn")).isFalse();

        TeamPlanAgent.PlanSubAgentConfig worker = new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard("worker", "worker_agent", "worker"),
                TeamPlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.get("cn")
        );
        assertThat(TeamPlanAgent.applyTeamPlanAgentPrompt(List.of("plain", worker), "cn")).isFalse();
        assertThat(worker.getSystemPrompt()).isEqualTo(TeamPlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.get("cn"));
    }

    @Test
    void buildTeamPlanAgentCardUsesResolvedLanguage() {
        AgentCard card = TeamPlanAgent.buildTeamPlanAgentCard("en");

        assertThat(card.getId()).isNull();
        assertThat(card.getName()).isEqualTo("plan_agent");
        assertThat(card.getDescription()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("en"));
    }
}
