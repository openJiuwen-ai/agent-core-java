/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.Map;

/**
 * Identity prompt section helpers.
 *
 * <p>Mirrors Python's {@code build_identity_section} in
 * {@code openjiuwen/harness/prompts/sections/identity.py}.
 */
public final class IdentitySection {

    private static final Map<String, String> IDENTITY = Map.of(
            "cn",
            "你是一个通用 AI 助手。请根据用户的需求，合理使用可用工具完成任务。\n"
                    + "在执行过程中保持目标聚焦，遇到问题时尝试不同策略。",
            "en",
            "You are a general-purpose AI assistant. Use available tools to complete tasks based on user needs.\n"
                    + "Stay focused on the goal during execution and try different strategies when encountering problems."
    );

    private IdentitySection() {
    }

    public static PromptSection buildIdentitySection(String language) {
        return new PromptSection(
                SectionName.IDENTITY,
                IDENTITY,
                10
        );
    }
}
