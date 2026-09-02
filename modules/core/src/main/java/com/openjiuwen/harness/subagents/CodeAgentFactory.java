/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * CodeAgentFactory.
 * 
 * @since 0.1.7
 */
public final class CodeAgentFactory {
    /**
     * CODE_AGENT_FACTORY_NAME.
     * 
     * @since 0.1.7
     */
    public static final String CODE_AGENT_FACTORY_NAME = "code_agent";

    /**
     * DEFAULT_CODE_AGENT_SYSTEM_PROMPT_EN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_CODE_AGENT_SYSTEM_PROMPT_EN =
        "You are an AI Coding Agent. Rules: Use tools whenever possible"
                + " (read/write/edit/grep/list/bash/code), don't guess file contents;make small,"
                + " reversible changes; clarify data structures and interfaces before modifying code;"
                + " provide testing/verification steps in your output.";

    /**
     * DEFAULT_CODE_AGENT_SYSTEM_PROMPT_CN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_CODE_AGENT_SYSTEM_PROMPT_CN =
        "你是一个 AI 编程助手，规则：能用工具就用工具（读/写/编辑/grep/list/bash/code），不要猜文件内容；变更要小、可回滚；" + "先澄清数据结构与接口，再动代码；输出给出测试/验证步骤。";

    /**
     * DEFAULT_CODE_AGENT_DESCRIPTION_EN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_CODE_AGENT_DESCRIPTION_EN =
        "You are a senior software engineer and coding agent, "
                + "excel at translating tasks into runnable code and verifiable results.";

    /**
     * DEFAULT_CODE_AGENT_DESCRIPTION_CN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_CODE_AGENT_DESCRIPTION_CN = "资深软件工程师与代码代理。擅长把任务落到可运行的代码与可验证的结果。";

    /**
     * CodeAgentFactory.
     * 
     * @since 0.1.7
     */
    private CodeAgentFactory() {
    }

    /**
     * buildCodeAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildCodeAgentConfig(String language) {
        return buildCodeAgentConfig(language, Map.of());
    }

    /**
     * buildCodeAgentConfig.
     * 
     * @param language language
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildCodeAgentConfig(String language, Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = SubAgentFactoryKwargsSupport.copy(factoryKwargs);
        SubAgentConfig config =
            SubAgentConfig.builder().agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(kwargs, "code_agent",
                    "en".equals(isResolved) ? DEFAULT_CODE_AGENT_DESCRIPTION_EN : DEFAULT_CODE_AGENT_DESCRIPTION_CN))
                    .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(kwargs,
                            "en".equals(isResolved)
                                    ? DEFAULT_CODE_AGENT_SYSTEM_PROMPT_EN
                                    : DEFAULT_CODE_AGENT_SYSTEM_PROMPT_CN))
                    .language(isResolved).maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 15))
                    .factoryName(CODE_AGENT_FACTORY_NAME).executionMode("ephemeral").role("coding")
                    .metadata(Map.of("requires_ask_user", true, "requires_confirm_interrupt", true,
                            "supports_coding_memory", true, "enable_task_planning", true))
                    .rails(SubAgentRailMergeSupport.mergeRails(
                            List.of(new SysOperationRail(), new AgentModeRail(), new AskUserRail(),
                                    new ConfirmInterruptRail(List.of("switch_mode")), buildCodingMemoryRail(kwargs)),
                            kwargs))
                    .subagents(List.of(ExploreAgentFactory.buildExploreAgentConfig(isResolved),
                            PlanAgentFactory.buildPlanAgentConfig(isResolved)))
                    .restrictToWorkDir(false).factoryKwargs(kwargs).build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * buildCodingMemoryRail.
     * 
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    private static CodingMemoryRail buildCodingMemoryRail(Map<String, Object> factoryKwargs) {
        Object configured = factoryKwargs != null ? factoryKwargs.get("embedding_config") : null;
        if (configured instanceof EmbeddingConfig embeddingConfig) {
            String codingMemoryDir = stringValue(factoryKwargs.get("coding_memory_dir"));
            boolean isProactive = booleanValue(factoryKwargs.get("coding_memory_proactive"), true);
            return new CodingMemoryRail(codingMemoryDir, embeddingConfig, isProactive);
        }
        return new CodingMemoryRail();
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value instanceof String str ? str : null;
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @param isDefaultValue isDefaultValue
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Object value, boolean isDefaultValue) {
        return value instanceof Boolean boolValue ? boolValue : isDefaultValue;
    }

    /**
     * createCodeAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createCodeAgent(String language, Workspace workspace) {
        SubAgentConfig spec = buildCodeAgentConfig(language);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), spec.toDeepAgentConfig(), workspace);
    }
}
