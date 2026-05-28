/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progressive-tool-rail prompt section builder.
 * <p>
 * Mirrors Python's {@code progressive_tool_rail} in
 * {@code openjiuwen.harness.prompts.sections.progressive_tool_rail}.
 */
public final class ProgressiveToolRailSection {

    private ProgressiveToolRailSection() {
    }

    private static final String CN = "# 工具发现\n"
            + "\n"
            + "- 使用 `search_tools` 搜索可用工具\n"
            + "- 使用 `load_tools` 加载指定工具\n"
            + "- 按需加载工具，保持工作区整洁\n";

    private static final String EN = "# Tool Discovery\n"
            + "\n"
            + "- Use `search_tools` to search for available tools\n"
            + "- Use `load_tools` to load specified tools\n"
            + "- Load tools on demand to keep the workspace clean\n";

    private static final Map<String, String> PROGRESSIVE = new LinkedHashMap<>();

    static {
        PROGRESSIVE.put("cn", CN);
        PROGRESSIVE.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.PROGRESSIVE_TOOL_RULES, PROGRESSIVE, 58);
    }
}
