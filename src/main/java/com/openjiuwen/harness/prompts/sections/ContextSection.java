/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context prompt section builder.
 * <p>
 * Mirrors Python's {@code context} in
 * {@code openjiuwen.harness.prompts.sections.context}.
 */
public final class ContextSection {

    private ContextSection() {
    }

    private static final String CN = "# 上下文管理\n"
            + "\n"
            + "- 合理管理对话上下文，避免信息丢失\n"
            + "- 长对话中适时总结关键信息\n";

    private static final String EN = "# Context Management\n"
            + "\n"
            + "- Manage conversation context to avoid information loss\n"
            + "- Summarize key information in long conversations\n";

    private static final Map<String, String> CONTEXT = new LinkedHashMap<>();

    static {
        CONTEXT.put("cn", CN);
        CONTEXT.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.CONTEXT, CONTEXT, 85);
    }
}
