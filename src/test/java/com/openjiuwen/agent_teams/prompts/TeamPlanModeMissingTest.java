/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_plan_mode_prompt.py}.
 */
class TeamPlanModeMissingTest {

    @Test
    void teamPlanModePromptIsTeamOriented() {
        assertThat(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_CN)
                .contains("Team.plan 模式已激活")
                .contains("真实的 Team Leader")
                .contains("强制团队执行语义")
                .contains("审批后第一步必须调用 `build_team`")
                .contains("禁止建议“不启动团队”“无需团队协作”");
        assertThat(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_EN)
                .contains("Team.plan mode is active")
                .contains("real Team Leader")
                .contains("Mandatory Team Execution Semantics")
                .contains("first execution step must be `build_team`")
                .contains("Never recommend \"no team needed\"");
    }

    @Test
    void getTeamPlanModePromptChoosesLanguage() {
        assertThat(TeamPlanMode.getTeamPlanModePrompt("cn")).isEqualTo(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_CN);
        assertThat(TeamPlanMode.getTeamPlanModePrompt("en")).isEqualTo(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_EN);
    }
}
