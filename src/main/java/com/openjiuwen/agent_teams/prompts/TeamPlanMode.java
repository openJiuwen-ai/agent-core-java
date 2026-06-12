/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Team.plan leader prompt-section builders.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/prompts/team_plan_mode.py}.</p>
 */
public final class TeamPlanMode {

    public static final String TEAM_PLAN_MODE_PROMPT_CN = buildTeamPlanModePromptTemplate("cn");
    public static final String TEAM_PLAN_MODE_PROMPT_EN = buildTeamPlanModePromptTemplate("en");

    private static final Map<String, String> ENTER_PLAN_MODE_MESSAGES = Map.of(
            statusKey("en", true), "enter_plan_mode has been called. Proceed with the workflow.",
            statusKey("en", false), "You have NOT called enter_plan_mode yet. Call it NOW as your first action.",
            statusKey("cn", true), "enter_plan_mode 已调用完成。请继续工作流。",
            statusKey("cn", false), "你尚未调用 enter_plan_mode。请立即调用它作为你的第一个操作。"
    );

    private static final Map<String, String> NO_PLAN_MESSAGES = Map.of(
            "en", "No plan file yet. Call enter_plan_mode first to create one.",
            "cn", "暂无 plan 文件。请先调用 enter_plan_mode 创建。"
    );

    private static final Map<String, String> PLAN_FILE_TEMPLATES = Map.of(
            statusKey("en", true),
            "A plan file already exists at {path}. You can read it and make incremental edits using the edit_file tool.",
            statusKey("en", false),
            "No plan file exists yet. You should create your plan at {path} using the write_file tool.",
            statusKey("cn", true),
            "计划文件已存在于 {path}。你可以使用 edit_file 工具读取并增量编辑它。",
            statusKey("cn", false),
            "计划文件尚不存在。你应该使用 write_file 工具在 {path} 创建计划。"
    );

    private TeamPlanMode() {
    }

    public static String buildTeamPlanModePromptTemplate(String language) {
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        return String.valueOf(PromptLoader.loadTemplate("team_plan_mode", resolvedLanguage).getContent()).strip();
    }

    public static String getTeamPlanModePrompt(String language) {
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        return "en".equals(resolvedLanguage) ? TEAM_PLAN_MODE_PROMPT_EN : TEAM_PLAN_MODE_PROMPT_CN;
    }

    public static String buildEnterPlanModeStatus(
            PlanFileProvider agent,
            PlanSession session,
            String language
    ) {
        Path planPath = agent == null ? null : agent.getPlanFilePath(session);
        String lang = "en".equals(HarnessPromptsPackage.resolveLanguage(language)) ? "en" : "cn";
        return ENTER_PLAN_MODE_MESSAGES.getOrDefault(
                statusKey(lang, planPath != null),
                "你尚未调用 enter_plan_mode。请立即调用它作为你的第一个操作。"
        );
    }

    public static String buildPlanFileInfo(
            PlanFileProvider agent,
            PlanSession session,
            String language
    ) {
        Path planPath = agent == null ? null : agent.getPlanFilePath(session);
        String lang = "en".equals(HarnessPromptsPackage.resolveLanguage(language)) ? "en" : "cn";
        if (planPath == null) {
            return NO_PLAN_MESSAGES.getOrDefault(lang, "暂无 plan 文件。请先调用 enter_plan_mode 创建。");
        }

        boolean exists = Files.exists(planPath);
        String fallback = "计划文件尚不存在。你应该使用 write_file 工具在 {path} 创建计划。";
        String template = PLAN_FILE_TEMPLATES.getOrDefault(statusKey(lang, exists), fallback);
        return template.replace("{path}", planPath.toString());
    }

    public static String buildTeamPlanModePrompt(
            String language,
            String enterPlanModeStatus,
            String planFileInfo
    ) {
        return getTeamPlanModePrompt(language)
                .replace("{enter_plan_mode_status}", enterPlanModeStatus)
                .replace("{plan_file_info}", planFileInfo);
    }

    public static PromptSection buildTeamPlanModeSection(
            String language,
            PlanFileProvider agent,
            PlanSession session
    ) {
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        String content = buildTeamPlanModePrompt(
                resolvedLanguage,
                buildEnterPlanModeStatus(agent, session, resolvedLanguage),
                buildPlanFileInfo(agent, session, resolvedLanguage)
        );
        Map<String, String> sectionContent = new LinkedHashMap<>();
        sectionContent.put(resolvedLanguage, content);
        return new PromptSection(SectionName.MODE_INSTRUCTIONS, sectionContent, 85);
    }

    /**
     * Plan file path provider used by the team.plan section builder.
     *
     * <p>Mirrors Python's {@code agent.get_plan_file_path(session)} use in
     * {@code openjiuwen/agent_teams/prompts/team_plan_mode.py}.</p>
     */
    @FunctionalInterface
    public interface PlanFileProvider {
        Path getPlanFilePath(PlanSession session);
    }

    /**
     * Session handle passed through to the plan file provider.
     *
     * <p>Mirrors Python's {@code Session} parameter use in
     * {@code openjiuwen/agent_teams/prompts/team_plan_mode.py}.</p>
     */
    public static final class PlanSession {
    }

    private static String statusKey(String language, boolean present) {
        return language + ":" + present;
    }
}
