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
}
