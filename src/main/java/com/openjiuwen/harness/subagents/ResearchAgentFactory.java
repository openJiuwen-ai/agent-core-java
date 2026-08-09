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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for the research subagent.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.research_agent} in
 * {@code openjiuwen/harness/subagents/research_agent.py}.</p>
 */
public final class ResearchAgentFactory {

    public static final String RESEARCH_AGENT_FACTORY_NAME = "research_agent";
    public static final String FACTORY_NAME = RESEARCH_AGENT_FACTORY_NAME;

    public static final Map<String, String> DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", "你是研究助理，负责围绕用户输入的主题开展调研，仅需返回最终研究结果。",
            "en", "You are a research assistant responsible for conducting research around the topic provided by the user."
                    + "Only return the final research results."
    );
    public static final Map<String, String> DEFAULT_RESEARCH_AGENT_DESCRIPTION = Map.of(
            "cn", "专注于研究调查任务，当用户想要调查某问题时，可使用该代理执行研究工作。每次只给这位研究员一个主题。",
            "en", "Focuses on research and investigation tasks. "
                    + "When users want to investigate a specific issue, this agent can be used to execute research work. "
                    + "Provide only one topic to this researcher at a time."
    );

    private ResearchAgentFactory() {
    }

    public static String getSystemPrompt(String language) {
        return defaultSystemPrompt(language);
    }

    public static String getDescription(String language) {
        return defaultDescription(language);
    }

    public static String defaultSystemPrompt(String language) {
        return DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT.get(ExploreAgent.resolveLanguage(language));
    }

    public static String defaultDescription(String language) {
        return DEFAULT_RESEARCH_AGENT_DESCRIPTION.get(ExploreAgent.resolveLanguage(language));
    }

    public static DeepAgentConfig.SubAgentConfig buildResearchAgentConfig(Object model) {
        return buildResearchAgentConfig(model, null, null, null, null, null, false, 15,
                null, null, null, null, null, null);
    }

    public static DeepAgentConfig.SubAgentConfig buildResearchAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            List<String> skills,
            Object backend,
            Object sysOperation,
            String language,
            String promptMode
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(RESEARCH_AGENT_FACTORY_NAME, RESEARCH_AGENT_FACTORY_NAME,
                defaultDescription(resolvedLanguage))
                : card;
        DeepAgentConfig config = baseConfig(
                model,
                finalCard,
                systemPrompt == null ? defaultSystemPrompt(resolvedLanguage) : systemPrompt,
                tools,
                rails,
                enableTaskLoop,
                maxIterations,
                workspace,
                skills,
                backend,
                sysOperation,
                resolvedLanguage,
                promptMode,
                mcps
        );

        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig(
                finalCard.getName(),
                finalCard.getDescription(),
                config.getSystemPrompt()
        );
        spec.setCard(finalCard);
        spec.setConfig(config);
        spec.setTools(tools);
        spec.setMcps(toObjectList(mcps));
        spec.setModel(model);
        spec.setRails(rails);
        spec.setSkills(skills);
        spec.setBackend(backend);
        spec.setWorkspace(workspace);
        spec.setSysOperation(sysOperation);
        spec.setLanguage(resolvedLanguage);
        spec.setPromptMode(promptMode);
        spec.setEnableTaskLoop(enableTaskLoop);
        spec.setMaxIterations(maxIterations);
        spec.setFactoryName(RESEARCH_AGENT_FACTORY_NAME);
        spec.setMetadata(ExploreAgent.metadata(RESEARCH_AGENT_FACTORY_NAME, maxIterations, mcps));
        return spec;
    }

    public static DeepAgent createResearchAgent(Object model) {
        return createResearchAgent(model, null, null, null, null, null, null, false, 15,
                null, null, null, null, null, null);
    }

    public static DeepAgent createResearchAgent(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentConfig.SubAgentConfig> subagents,
            List<DeepAgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            List<String> skills,
            Object backend,
            Object sysOperation,
            String language,
            String promptMode
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(RESEARCH_AGENT_FACTORY_NAME, RESEARCH_AGENT_FACTORY_NAME,
                defaultDescription(resolvedLanguage))
                : card;
        List<DeepAgentRail> finalRails = rails == null ? List.of(new SysOperationRail()) : List.copyOf(rails);
        DeepAgentConfig config = baseConfig(
                model,
                finalCard,
                systemPrompt == null ? defaultSystemPrompt(resolvedLanguage) : systemPrompt,
                tools,
                finalRails,
                enableTaskLoop,
                maxIterations,
                workspace,
                skills,
                backend,
                sysOperation,
                resolvedLanguage,
                promptMode,
                mcps
        );
        config.setSubagents(toSubagentMap(subagents));

        DeepAgent agent = new DeepAgent(finalCard);
        agent.configure(config);
        return agent;
    }

    private static DeepAgentConfig baseConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<DeepAgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            List<String> skills,
            Object backend,
            Object sysOperation,
            String language,
            String promptMode,
            List<McpServerConfig> mcps
    ) {
        DeepAgentConfig config = ExploreAgent.baseConfig(
                model,
                card,
                systemPrompt,
                tools,
                rails,
                language,
                enableTaskLoop
        );
        config.setCard(card);
        config.setMaxIterations(maxIterations);
        config.setWorkspace(workspace);
        config.setSkills(skills);
        config.setBackend(backend);
        config.setSysOperation(sysOperation);
        config.setPromptMode(promptMode);
        config.setMcps(toObjectList(mcps));
        return config;
    }

    private static Map<String, DeepAgentConfig.SubAgentConfig> toSubagentMap(
            List<DeepAgentConfig.SubAgentConfig> subagents
    ) {
        Map<String, DeepAgentConfig.SubAgentConfig> result = new LinkedHashMap<>();
        if (subagents == null) {
            return result;
        }
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            if (spec != null && spec.getName() != null && !spec.getName().isBlank()) {
                result.put(spec.getName(), spec);
            }
        }
        return result;
    }

    private static List<Object> toObjectList(List<?> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
