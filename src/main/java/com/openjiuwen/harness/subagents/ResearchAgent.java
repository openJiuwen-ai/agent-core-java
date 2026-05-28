/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

/**
 * Research agent configuration and factory.
 * <p>
 * Mirrors Python's {@code research_agent} in
 * {@code openjiuwen.harness.subagents.research_agent}.
 */
public final class ResearchAgent {

    private ResearchAgent() {
    }

    public static final String FACTORY_NAME = "research_agent";

    private static final String SYSTEM_PROMPT_CN = "你是一个研究助手，擅长搜索文档、分析代码和汇总技术信息。";
    private static final String SYSTEM_PROMPT_EN = "You are a research agent, skilled at searching documentation, analyzing code, and summarizing technical information.";

    private static final String DESCRIPTION_CN = "研究代理。擅长文档检索、代码分析和技术信息汇总。";
    private static final String DESCRIPTION_EN = "Research agent. Excels at documentation retrieval, code analysis, and technical information summarization.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }
}
