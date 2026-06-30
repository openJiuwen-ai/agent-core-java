/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class ResearchAgentFactory {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String RESEARCH_AGENT_FACTORY_NAME = "research_agent";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT_EN = "You are a research assistant responsible "
            + "for conducting research around the topic provided by the user."
            + "Only return the final research results.";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT_CN = "你是研究助理，负责围绕用户输入的主题开展调研，仅需返回最终研究结果。";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_RESEARCH_AGENT_DESCRIPTION_EN = "Focuses on research and investigation tasks. "
            + "When users want to investigate a specific issue, this agent can be used to execute research work. "
            + "Provide only one topic to this researcher at a time.";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_RESEARCH_AGENT_DESCRIPTION_CN = "专注于研究调查任务，"
            + "当用户想要调查某问题时，可使用该代理执行研究工作。每次只给这位研究员一个主题。";

    private ResearchAgentFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static SubAgentConfig buildResearchAgentConfig(String language) {
        return buildResearchAgentConfig(language, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static SubAgentConfig buildResearchAgentConfig(String language, Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = SubAgentFactoryKwargsSupport.copy(factoryKwargs);
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(
                        kwargs,
                        "research_agent",
                        "en".equals(isResolved)
                                ? DEFAULT_RESEARCH_AGENT_DESCRIPTION_EN
                                : DEFAULT_RESEARCH_AGENT_DESCRIPTION_CN
                ))
                .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(
                        kwargs,
                        "en".equals(isResolved)
                                ? DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT_EN
                                : DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT_CN
                ))
                .language(isResolved)
                .maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 15))
                .factoryName(RESEARCH_AGENT_FACTORY_NAME)
                .executionMode("ephemeral")
                .role("research")
                .rails(SubAgentRailMergeSupport.mergeRails(List.of(new SysOperationRail()), kwargs))
                .restrictToWorkDir(false)
                .factoryKwargs(kwargs)
                .build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static DeepAgent createResearchAgent(String language, Workspace workspace) {
        SubAgentConfig spec = buildResearchAgentConfig(language);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), spec.toDeepAgentConfig(), workspace);
    }
}
