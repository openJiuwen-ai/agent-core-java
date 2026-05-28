/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

/**
 * Code agent configuration and factory.
 * <p>
 * Mirrors Python's {@code code_agent} in
 * {@code openjiuwen.harness.subagents.code_agent}.
 */
public final class CodeAgent {

    private CodeAgent() {
    }

    public static final String FACTORY_NAME = "code_agent";

    private static final String SYSTEM_PROMPT_CN =
            "你是一个 AI 编程助手，规则：能用工具就用工具（读/写/编辑/grep/list/bash/code），不要猜文件内容；"
            + "变更要小、可回滚；先澄清数据结构与接口，再动代码；输出给出测试/验证步骤。";

    private static final String SYSTEM_PROMPT_EN =
            "You are an AI Coding Agent. "
            + "Rules: Use tools whenever possible (read/write/edit/grep/list/bash/code), don't guess file contents;"
            + "make small, reversible changes; clarify data structures and interfaces before modifying code; "
            + "provide testing/verification steps in your output.";

    private static final String DESCRIPTION_CN = "资深软件工程师与代码代理。擅长把任务落到可运行的代码与可验证的结果。";
    private static final String DESCRIPTION_EN = "Senior software engineer and coding agent. Excels at translating tasks into runnable code and verifiable results.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }
}
