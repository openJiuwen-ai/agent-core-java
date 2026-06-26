/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plan-mode prompt section helpers.
 *
 * <p>Mirrors Python's {@code agent_mode} helpers in
 * {@code openjiuwen/harness/prompts/sections/agent_mode.py}.</p>
 */
public final class AgentModeSection {

    public static final String PLAN_MODE_PROMPT_CN = """
            Plan 模式已激活。用户希望你先制定计划，不要求执行。
            你不得进行任何修改，除非是写入明确的 plan 文件。
            """;

    public static final String PLAN_MODE_PROMPT_EN = """
            Plan mode is active. The user wants you to plan first.
            Do not make modifications except writing the explicit plan file.
            """;

    private AgentModeSection() {
    }

    public static PromptSection buildPlanModeSection(String language, String planFilePath, boolean planExists) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String prompt = "en".equals(resolvedLanguage) ? PLAN_MODE_PROMPT_EN : PLAN_MODE_PROMPT_CN;
        String enterStatus = buildEnterPlanModeStatus(planFilePath, resolvedLanguage);
        String planInfo = buildPlanFileInfo(planFilePath, planExists, resolvedLanguage);
        return new PromptSection(
                SectionName.MODE_INSTRUCTIONS,
                Map.of(resolvedLanguage, prompt + "\n" + enterStatus + "\n" + planInfo),
                5
        );
    }

    public static PromptSection buildPlanModeSection(
            String language,
            String planFilePath,
            boolean planExists,
            DeepAgent agent,
            Object session
    ) {
        if (agent == null) {
            return buildPlanModeSection(language, planFilePath, planExists);
        }
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String resolvedPlanFilePath = agent.getPlanFilePath(session);
        boolean resolvedPlanExists = resolvedPlanFilePath != null
                && !resolvedPlanFilePath.isBlank()
                && Files.exists(Path.of(resolvedPlanFilePath));
        String prompt = "en".equals(resolvedLanguage) ? PLAN_MODE_PROMPT_EN : PLAN_MODE_PROMPT_CN;
        String enterStatus = buildEnterPlanModeStatus(resolvedPlanFilePath, resolvedLanguage);
        String planInfo = buildPlanFileInfo(resolvedPlanFilePath, resolvedPlanExists, resolvedLanguage);
        return new PromptSection(
                SectionName.MODE_INSTRUCTIONS,
                Map.of(resolvedLanguage, prompt + "\n" + enterStatus + "\n" + planInfo),
                5
        );
    }

    public static String buildEnterPlanModeStatus(String planFilePath, String language) {
        if (planFilePath == null || planFilePath.isBlank()) {
            return "en".equals(language)
                    ? "You have NOT called enter_plan_mode yet. Call it NOW as your first action."
                    : "你尚未调用 enter_plan_mode。请立即调用它作为你的第一个操作。";
        }
        return "en".equals(language)
                ? "enter_plan_mode has been called. Proceed with the workflow."
                : "enter_plan_mode 已调用完成。请继续工作流。";
    }

    public static String buildPlanFileInfo(String planFilePath, boolean planExists, String language) {
        if (planFilePath == null || planFilePath.isBlank()) {
            return "en".equals(language)
                    ? "No plan file yet. Call enter_plan_mode first to create one."
                    : "尚无 plan 文件。请先调用 enter_plan_mode 创建。";
        }
        String status = planExists || Files.exists(Path.of(planFilePath))
                ? ("en".equals(language) ? "exists" : "已存在")
                : ("en".equals(language) ? "does not exist yet" : "尚不存在");
        return ("en".equals(language) ? "Plan file: " : "计划文件：") + planFilePath + " (" + status + ")";
    }
}
