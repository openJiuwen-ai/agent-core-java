/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identity prompt section builder.
 * <p>
 * Mirrors Python's {@code identity} in
 * {@code openjiuwen.harness.prompts.sections.identity}.
 */
public final class IdentitySection {

    private IdentitySection() {
    }

    private static final Map<String, String> IDENTITY = new LinkedHashMap<>();

    static {
        IDENTITY.put("cn",
                "你是一个通用 AI 助手。请根据用户的需求，合理使用可用工具完成任务。\n"
                + "在执行过程中保持目标聚焦，遇到问题时尝试不同策略。");
        IDENTITY.put("en",
                "You are a general-purpose AI assistant. Use available tools to complete tasks based on user needs.\n"
                + "Stay focused on the goal during execution and try different strategies when encountering problems.");
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.IDENTITY, IDENTITY, 10);
    }
}
