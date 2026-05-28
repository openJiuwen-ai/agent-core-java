/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

/**
 * Explore agent configuration and factory.
 * <p>
 * Mirrors Python's {@code explore_agent} in
 * {@code openjiuwen.harness.subagents.explore_agent}.
 */
public final class ExploreAgent {

    private ExploreAgent() {
    }

    public static final String FACTORY_NAME = "explore_agent";

    private static final String SYSTEM_PROMPT_CN = "你是一个代码探索助手，擅长快速搜索和理解代码库结构。";
    private static final String SYSTEM_PROMPT_EN = "You are a code exploration agent, skilled at quickly searching and understanding codebase structure.";

    private static final String DESCRIPTION_CN = "代码探索代理。擅长快速定位代码模式和理解架构。";
    private static final String DESCRIPTION_EN = "Code exploration agent. Excels at quickly locating code patterns and understanding architecture.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }
}
