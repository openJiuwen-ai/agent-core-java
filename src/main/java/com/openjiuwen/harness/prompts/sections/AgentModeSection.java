/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent-mode prompt section builder.
 * <p>
 * Mirrors Python's {@code agent_mode} in
 * {@code openjiuwen.harness.prompts.sections.agent_mode}.
 */
public final class AgentModeSection {

    private AgentModeSection() {
    }

    private static final String CN = "# 模式切换\n"
            + "\n"
            + "- `switch_mode`: 切换代理模式\n"
            + "- `enter_plan_mode`: 进入规划模式（仅可使用规划相关工具）\n"
            + "- `exit_plan_mode`: 退出规划模式，恢复正常工具集\n"
            + "- 规划模式下仅允许读取、搜索和规划操作\n";

    private static final String EN = "# Mode Switching\n"
            + "\n"
            + "- `switch_mode`: Switch agent mode\n"
            + "- `enter_plan_mode`: Enter plan mode (only planning-related tools available)\n"
            + "- `exit_plan_mode`: Exit plan mode, restore full tool set\n"
            + "- In plan mode, only read, search, and planning operations are allowed\n";

    private static final Map<String, String> MODE = new LinkedHashMap<>();

    static {
        MODE.put("cn", CN);
        MODE.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.MODE_INSTRUCTIONS, MODE, 55);
    }

    /**
     * Build the dynamic plan-mode section used while the agent is in plan mode.
     */
    public static PromptSection buildPlanModeSection(
            String language,
            String planFilePath,
            boolean planExists,
            boolean enterPlanModeCalled
    ) {
        String lang = "en".equalsIgnoreCase(language) ? "en" : "cn";
        String statusEn = enterPlanModeCalled
                ? "enter_plan_mode has already been called. Continue the workflow."
                : "You have NOT called enter_plan_mode yet. Call it now as your first action.";
        String statusCn = enterPlanModeCalled
                ? "enter_plan_mode 已调用完成，请继续后续工作流。"
                : "你还没有调用 enter_plan_mode，请先调用它。";
        String planInfoEn;
        String planInfoCn;
        if (planFilePath == null || planFilePath.isBlank()) {
            planInfoEn = "No plan file yet. Call enter_plan_mode first to create one.";
            planInfoCn = "当前还没有 plan 文件，请先调用 enter_plan_mode 创建。";
        } else if (planExists) {
            planInfoEn = "Plan file: " + planFilePath + ". Read it and update it incrementally.";
            planInfoCn = "Plan 文件路径：" + planFilePath + "。请基于它增量更新。";
        } else {
            planInfoEn = "Plan file should be created at: " + planFilePath + ".";
            planInfoCn = "Plan 文件将创建于：" + planFilePath + "。";
        }
        String en = "# Plan Mode\n\n"
                + "Plan mode is active. Only planning and read-only work is allowed.\n\n"
                + "## First Step\n"
                + statusEn + "\n\n"
                + "## Plan File\n"
                + planInfoEn + "\n\n"
                + "- Only the plan file may be edited in this mode.\n"
                + "- Todo and session tools are hidden in this mode.\n"
                + "- End planning by calling `exit_plan_mode`.\n";
        String cn = "# 规划模式\n\n"
                + "当前已进入 plan mode，只允许规划和只读操作。\n\n"
                + "## 首要步骤\n"
                + statusCn + "\n\n"
                + "## Plan 文件\n"
                + planInfoCn + "\n\n"
                + "- 此模式下只允许编辑 plan 文件。\n"
                + "- todo 与 session 相关工具在此模式下会被隐藏。\n"
                + "- 完成规划后请调用 `exit_plan_mode`。\n";
        return new PromptSection(
                SectionName.MODE_INSTRUCTIONS,
                Map.of("cn", cn, "en", en),
                55
        );
    }
}
