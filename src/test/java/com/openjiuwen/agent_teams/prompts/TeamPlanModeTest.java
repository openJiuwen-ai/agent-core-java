/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests team.plan mode prompt-section builders.
 *
 * <p>Mirrors Python's module behavior in
 * {@code openjiuwen/agent_teams/prompts/team_plan_mode.py}.</p>
 */
class TeamPlanModeTest {

    @BeforeEach
    void clearCache() {
        PromptLoader.clearCacheForTests();
    }

    @Test
    void promptConstantsLoadMarkdownTemplates() {
        assertThat(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_CN)
                .isEqualTo(String.valueOf(PromptLoader.loadTemplate("team_plan_mode", "cn").getContent()).strip());
        assertThat(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_EN)
                .isEqualTo(String.valueOf(PromptLoader.loadTemplate("team_plan_mode", "en").getContent()).strip());
        assertThat(TeamPlanMode.getTeamPlanModePrompt("en")).isEqualTo(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_EN);
        assertThat(TeamPlanMode.getTeamPlanModePrompt("cn")).isEqualTo(TeamPlanMode.TEAM_PLAN_MODE_PROMPT_CN);
    }

    @Test
    void enterPlanModeStatusDependsOnPlanPathPresence(@TempDir Path tempDir) {
        TeamPlanMode.PlanSession session = new TeamPlanMode.PlanSession();
        Path planFile = tempDir.resolve("team-plan.md");

        assertThat(TeamPlanMode.buildEnterPlanModeStatus(ignored -> planFile, session, "en"))
                .isEqualTo("enter_plan_mode has been called. Proceed with the workflow.");
        assertThat(TeamPlanMode.buildEnterPlanModeStatus(ignored -> null, session, "en"))
                .isEqualTo("You have NOT called enter_plan_mode yet. Call it NOW as your first action.");
        assertThat(TeamPlanMode.buildEnterPlanModeStatus(ignored -> planFile, session, "cn"))
                .isEqualTo("enter_plan_mode 已调用完成。请继续工作流。");
        assertThat(TeamPlanMode.buildEnterPlanModeStatus(ignored -> null, session, "cn"))
                .isEqualTo("你尚未调用 enter_plan_mode。请立即调用它作为你的第一个操作。");
    }

    @Test
    void planFileInfoHandlesMissingPathExistingFileAndFuturePath(@TempDir Path tempDir) throws Exception {
        TeamPlanMode.PlanSession session = new TeamPlanMode.PlanSession();
        Path existing = tempDir.resolve("existing-plan.md");
        Path future = tempDir.resolve("future-plan.md");
        java.nio.file.Files.writeString(existing, "plan");

        assertThat(TeamPlanMode.buildPlanFileInfo(ignored -> null, session, "en"))
                .isEqualTo("No plan file yet. Call enter_plan_mode first to create one.");
        assertThat(TeamPlanMode.buildPlanFileInfo(ignored -> existing, session, "en"))
                .contains("A plan file already exists at " + existing)
                .contains("incremental edits");
        assertThat(TeamPlanMode.buildPlanFileInfo(ignored -> future, session, "en"))
                .contains("No plan file exists yet. You should create your plan at " + future);
        assertThat(TeamPlanMode.buildPlanFileInfo(ignored -> future, session, "cn"))
                .contains("计划文件尚不存在")
                .contains(future.toString());
    }

    @Test
    void buildPromptReplacesBothTemplateVariables() {
        String prompt = TeamPlanMode.buildTeamPlanModePrompt(
                "en",
                "status-line",
                "file-info-line"
        );

        assertThat(prompt)
                .contains("Team.plan mode is active")
                .contains("status-line")
                .contains("file-info-line")
                .doesNotContain("{enter_plan_mode_status}")
                .doesNotContain("{plan_file_info}");
    }

    @Test
    void buildSectionUsesModeInstructionsNameAndPriority(@TempDir Path tempDir) {
        TeamPlanMode.PlanSession session = new TeamPlanMode.PlanSession();
        Path planFile = tempDir.resolve("plan.md");
        PromptSection section = TeamPlanMode.buildTeamPlanModeSection("en", ignored -> planFile, session);

        assertThat(section.getName()).isEqualTo(SectionName.MODE_INSTRUCTIONS);
        assertThat(section.getPriority()).isEqualTo(85);
        assertThat(section.render("en"))
                .contains("enter_plan_mode has been called")
                .contains("No plan file exists yet. You should create your plan at " + planFile);
    }
}
