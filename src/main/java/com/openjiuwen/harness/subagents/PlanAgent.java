/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.List;

/**
 * Factory helpers for Plan subagents.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.plan_agent} in
 * {@code openjiuwen/harness/subagents/plan_agent.py}.</p>
 */
public final class PlanAgent {

    public static final String FACTORY_NAME = "plan_agent";

    public static final String PLAN_AGENT_SYSTEM_PROMPT_CN =
            "你是架构设计与规划专家，基于提供的代码探索背景和用户需求，设计清晰、可执行的实现方案。"
                    + "这是纯规划任务，禁止创建、修改、删除、移动或复制文件。";
    public static final String PLAN_AGENT_SYSTEM_PROMPT_EN =
            "You are a software architect and planning specialist. Design a clear, actionable implementation approach. "
                    + "This is a read-only planning task; do not modify files.";
    public static final String PLAN_AGENT_DESCRIPTION_CN =
            "架构设计专家。基于代码探索结果设计实现方案，生成详细的实现计划。";
    public static final String PLAN_AGENT_DESCRIPTION_EN =
            "Architecture design specialist that produces implementation plans from exploration context.";

    private PlanAgent() {
    }

    public static DeepAgentConfig.SubAgentConfig buildPlanAgentConfig(Object model) {
        return buildPlanAgentConfig(model, null, null, null, null, null, "cn", false, 25);
    }

    public static DeepAgentConfig.SubAgentConfig buildPlanAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            String language,
            boolean enableTaskLoop,
            int maxIterations
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(FACTORY_NAME, FACTORY_NAME, defaultDescription(resolvedLanguage))
                : card;
        List<DeepAgentRail> finalRails = rails == null ? List.of(new SysOperationRail()) : List.copyOf(rails);
        DeepAgentConfig config = ExploreAgent.baseConfig(
                model,
                finalCard,
                systemPrompt == null ? defaultSystemPrompt(resolvedLanguage) : systemPrompt,
                tools,
                finalRails,
                resolvedLanguage,
                enableTaskLoop
        );
        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig(
                finalCard.getName(),
                finalCard.getDescription(),
                config.getSystemPrompt()
        );
        spec.setCard(finalCard);
        spec.setConfig(config);
        spec.setMetadata(ExploreAgent.metadata(FACTORY_NAME, maxIterations, mcps));
        return spec;
    }

    public static DeepAgent createPlanAgent(Object model, String language) {
        DeepAgentConfig.SubAgentConfig spec = buildPlanAgentConfig(
                model, null, null, null, null, null, language, false, 25);
        DeepAgent agent = new DeepAgent(spec.getCard());
        agent.configure(spec.getConfig());
        return agent;
    }

    public static String defaultSystemPrompt(String language) {
        return "en".equals(ExploreAgent.resolveLanguage(language))
                ? PLAN_AGENT_SYSTEM_PROMPT_EN
                : PLAN_AGENT_SYSTEM_PROMPT_CN;
    }

    public static String defaultDescription(String language) {
        return "en".equals(ExploreAgent.resolveLanguage(language))
                ? PLAN_AGENT_DESCRIPTION_EN
                : PLAN_AGENT_DESCRIPTION_CN;
    }
}
