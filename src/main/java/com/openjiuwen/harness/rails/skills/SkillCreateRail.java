/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Independent rail for one-dimensional skill creation proposals.
 *
 * <p>Mirrors Python's {@code SkillCreateRail} in
 * {@code openjiuwen/harness/rails/skills/skill_create_rail.py}.</p>
 */
public class SkillCreateRail extends EvolutionRail {

    private static final String FOLLOW_UP_PROMPT_CN = """
            **重要：你必须先向用户确认，不可跳过此步骤。**
            系统检测到对话中存在可复用模式，可能值得创建新技能。请按以下步骤执行：
            1. 直接询问或调用 ask_user 工具向用户确认：
               - 问题："我检测到您可能值得创建一个新技能。是否创建？"
               - 选项：["创建"，"跳过"，"自定义指令：（请描述需求）"]
            2. 如果用户选择"创建"或提供了自定义指令，请调用 **skill-creator** 技能，根据用户的要求和当前对话上下文执行技能创建。
               新技能应保存到技能目录：%s""";
    private static final String FOLLOW_UP_PROMPT_EN = """
            **Important: You MUST confirm with the user first. Do not skip this step.**
            The system detected a reusable pattern that may be worth creating as a new skill. Please follow these steps:
            1. Directly inquire or invoke the `ask_user` tool to confirm with the user:
               - Question: "I detected a pattern that may be worth creating as a new skill. Create it?"
               - Options: ["Create", "Skip", "Custom instruction: (describe your needs)"]
            2. If user chooses "Create" or provides a custom instruction, invoke the **skill-creator** skill to execute the skill creation.
               Save the new skill to: %s""";

    private final Path skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int toolCallThreshold;
    private final int toolDiversityThreshold;
    private boolean proposalSent;

    public SkillCreateRail(Path skillsDir) {
        this(skillsDir, "cn", true, 10, 5);
    }

    public SkillCreateRail(
            Path skillsDir,
            String language,
            boolean autoTrigger,
            int toolCallThreshold,
            int toolDiversityThreshold
    ) {
        super(100, EvolutionTriggerPoint.NONE, true, Set.of());
        setPriority(85);
        this.skillsDir = skillsDir;
        this.language = "en".equals(language) ? "en" : "cn";
        this.autoTrigger = autoTrigger;
        this.toolCallThreshold = toolCallThreshold;
        this.toolDiversityThreshold = toolDiversityThreshold;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        super.beforeInvoke(ctx);
        proposalSent = false;
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        super.afterTaskIteration(ctx);
        if (!autoTrigger || proposalSent || !shouldProposeNewSkill()) {
            return;
        }
        if (ctx.getAgent() == null) {
            return;
        }
        String prompt = buildFollowUpPrompt();
        ctx.put("skill_create_follow_up", prompt);
        ctx.getAgent().loopController().enqueueFollowUp(prompt);
        proposalSent = true;
    }

    public boolean shouldProposeNewSkill() {
        Set<String> uniqueTools = new HashSet<>();
        int totalCalls = 0;
        for (Map<String, Object> step : buildTrajectory()) {
            Object values = step.get("values");
            if (!(values instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("tool_name");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            totalCalls += 1;
            uniqueTools.add(String.valueOf(name));
        }
        return totalCalls >= toolCallThreshold && uniqueTools.size() >= toolDiversityThreshold;
    }

    public Path getSkillsDir() {
        return skillsDir;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isAutoTrigger() {
        return autoTrigger;
    }

    public int getToolCallThreshold() {
        return toolCallThreshold;
    }

    public int getToolDiversityThreshold() {
        return toolDiversityThreshold;
    }

    public EvolutionTriggerPoint getEvolutionTriggerPoint() {
        return EvolutionTriggerPoint.NONE;
    }

    public boolean isProposalSent() {
        return proposalSent;
    }

    private String buildFollowUpPrompt() {
        String dir = skillsDir == null ? "" : skillsDir.toString();
        if ("en".equals(language)) {
            return FOLLOW_UP_PROMPT_EN.formatted(dir);
        }
        return FOLLOW_UP_PROMPT_CN.formatted(dir);
    }
}
