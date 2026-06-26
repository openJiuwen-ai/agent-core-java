/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_plan_agent_prompt.py}.
 */
class TeamPlanAgentPromptMissingTest {

    @Test
    void teamPlanAgentPromptDictMatchesConstants() {
        assertThat(TeamPlanAgent.DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT.get("cn"))
                .isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN);
        assertThat(TeamPlanAgent.DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT.get("en"))
                .isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void teamPlanAgentPromptIsTeamOriented() {
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN)
                .contains("团队执行方案")
                .contains("强制团队执行语义")
                .contains("先调用 build_team")
                .contains("无需团队协作");
        assertThat(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN)
                .contains("team execution plan")
                .contains("MANDATORY TEAM EXECUTION SEMANTICS")
                .contains("first calls build_team")
                .contains("\"no team needed\"");
    }

    @Test
    void applyTeamPlanAgentPromptReplacesBuiltinDefault() {
        TeamPlanAgent.PlanSubAgentConfig spec = new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard("plan", "plan_agent", "default"),
                TeamPlanAgent.DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.get("en")
        );

        boolean changed = TeamPlanAgent.applyTeamPlanAgentPrompt(List.of(spec), "en");

        assertThat(changed).isTrue();
        assertThat(spec.getAgentCard().getDescription()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_DESC.get("en"));
        assertThat(spec.getSystemPrompt()).isEqualTo(TeamPlanAgent.TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN);
    }

    @Test
    void applyTeamPlanAgentPromptPreservesCustomPrompt() {
        TeamPlanAgent.PlanSubAgentConfig spec = new TeamPlanAgent.PlanSubAgentConfig(
                new AgentCard("plan", "plan_agent", "custom"),
                "custom"
        );

        boolean changed = TeamPlanAgent.applyTeamPlanAgentPrompt(List.of(spec), "en");

        assertThat(changed).isFalse();
        assertThat(spec.getSystemPrompt()).isEqualTo("custom");
    }
}
