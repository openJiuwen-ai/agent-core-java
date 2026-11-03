/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for Explore subagents.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.explore_agent} in
 * {@code openjiuwen/harness/subagents/explore_agent.py}.</p>
 */
public final class ExploreAgent {

    public static final String FACTORY_NAME = "explore_agent";

    public static final String DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN =
            ExploreAgentFactory.DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN;
    public static final String DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN =
            ExploreAgentFactory.DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN;
    public static final String DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN =
            ExploreAgentFactory.DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN;
    public static final String DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN =
            ExploreAgentFactory.DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN;

    private ExploreAgent() {
    }

    public static DeepAgentConfig.SubAgentConfig buildExploreAgentConfig(Object model) {
        return buildExploreAgentConfig(model, null, null, null, null, "cn", false, 15);
    }

    public static DeepAgentConfig.SubAgentConfig buildExploreAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            String language,
            boolean enableTaskLoop,
            int maxIterations
    ) {
        String resolvedLanguage = resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(FACTORY_NAME, FACTORY_NAME, defaultDescription(resolvedLanguage))
                : card;
        DeepAgentConfig config = baseConfig(model, finalCard, systemPrompt == null
                ? defaultSystemPrompt(resolvedLanguage)
                : systemPrompt, tools, List.of(new SysOperationRail(false, true)), resolvedLanguage, enableTaskLoop);
        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig(
                finalCard.getName(),
                finalCard.getDescription(),
                config.getSystemPrompt()
        );
        spec.setCard(finalCard);
        spec.setConfig(config);
        spec.setMetadata(metadata(FACTORY_NAME, maxIterations, mcps));
        return spec;
    }

    public static DeepAgent createExploreAgent(Object model, String language) {
        DeepAgentConfig.SubAgentConfig spec = buildExploreAgentConfig(model, null, null, null, null, language, false, 15);
        DeepAgent agent = new DeepAgent(spec.getCard());
        agent.configure(spec.getConfig());
        return agent;
    }

    public static String defaultSystemPrompt(String language) {
        return "en".equals(resolveLanguage(language))
                ? DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN
                : DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN;
    }

    public static String defaultDescription(String language) {
        return "en".equals(resolveLanguage(language))
                ? DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN
                : DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN;
    }

    static String resolveLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "cn";
    }

    static DeepAgentConfig baseConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<DeepAgentRail> rails,
            String language,
            boolean enableTaskLoop
    ) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setLanguage(language);
        config.setSystemPrompt(systemPrompt);
        config.setTools(tools);
        config.setRails(rails);
        config.setEnableTaskLoop(enableTaskLoop);
        return config;
    }

    static Map<String, Object> metadata(String factoryName, int maxIterations, List<McpServerConfig> mcps) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("factory_name", factoryName);
        metadata.put("max_iterations", maxIterations);
        metadata.put("mcps", mcps == null ? List.of() : List.copyOf(mcps));
        return metadata;
    }
}
