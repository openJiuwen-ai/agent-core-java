/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

/**
 * Plan agent configuration and factory.
 * <p>
 * Mirrors Python's {@code plan_agent} in
 * {@code openjiuwen.harness.subagents.plan_agent}.
 */
public final class PlanAgent {

    private PlanAgent() {
    }

    public static final String FACTORY_NAME = "plan_agent";

    private static final String SYSTEM_PROMPT_CN = "你是一个规划助手，擅长拆解任务、制定执行计划和评估风险。";
    private static final String SYSTEM_PROMPT_EN = "You are a planning agent, skilled at decomposing tasks, creating execution plans, and assessing risks.";

    private static final String DESCRIPTION_CN = "规划代理。擅长拆解复杂任务和制定可行方案。";
    private static final String DESCRIPTION_EN = "Planning agent. Excels at decomposing complex tasks and creating feasible plans.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }
}
