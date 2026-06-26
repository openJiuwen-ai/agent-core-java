/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmRail;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.workspace.WorkspaceNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for the coding subagent.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.code_agent} in
 * {@code openjiuwen/harness/subagents/code_agent.py}.</p>
 */
public final class CodeAgentFactory {

    public static final String CODE_AGENT_FACTORY_NAME = "code_agent";
    public static final String FACTORY_NAME = CODE_AGENT_FACTORY_NAME;

    public static final Map<String, String> DEFAULT_CODE_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", "你是一个 AI 编程助手，规则：能用工具就用工具（读/写/编辑/grep/list/bash/code），不要猜文件内容；变更要小、可回滚；"
                    + "先澄清数据结构与接口，再动代码；输出给出测试/验证步骤。",
            "en", "You are an AI Coding Agent. "
                    + "Rules: Use tools whenever possible (read/write/edit/grep/list/bash/code), don't guess file contents;"
                    + "make small, reversible changes; clarify data structures and interfaces before modifying code; "
                    + "provide testing/verification steps in your output."
    );
    public static final Map<String, String> DEFAULT_CODE_AGENT_DESCRIPTION = Map.of(
            "cn", "资深软件工程师与代码代理。擅长把任务落到可运行的代码与可验证的结果。",
            "en", "You are a senior software engineer and coding agent, "
                    + "excel at translating tasks into runnable code and verifiable results."
    );

    private CodeAgentFactory() {
    }

    public static String getSystemPrompt(String language) {
        return defaultSystemPrompt(language);
    }

    public static String getDescription(String language) {
        return defaultDescription(language);
    }

    public static String defaultSystemPrompt(String language) {
        return DEFAULT_CODE_AGENT_SYSTEM_PROMPT.get(ExploreAgent.resolveLanguage(language));
    }

    public static String defaultDescription(String language) {
        return DEFAULT_CODE_AGENT_DESCRIPTION.get(ExploreAgent.resolveLanguage(language));
    }

    public static DeepAgentConfig.SubAgentConfig buildCodeAgentConfig(Object model) {
        return buildCodeAgentConfig(model, null, null, null, null, null, false, 15,
                null, null, null, null, null, null, null);
    }

    public static DeepAgentConfig.SubAgentConfig buildCodeAgentConfig(
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
            String promptMode,
            EmbeddingConfig embeddingConfig
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(CODE_AGENT_FACTORY_NAME, CODE_AGENT_FACTORY_NAME,
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
        spec.setFactoryName(CODE_AGENT_FACTORY_NAME);
        if (embeddingConfig != null) {
            spec.setFactoryKwargs(Map.of("embedding_config", embeddingConfig));
        }
        Map<String, Object> metadata = new LinkedHashMap<>(ExploreAgent.metadata(
                CODE_AGENT_FACTORY_NAME, maxIterations, mcps));
        if (embeddingConfig != null) {
            metadata.put("embedding_config", embeddingConfig);
        }
        spec.setMetadata(metadata);
        return spec;
    }

    public static DeepAgent createCodeAgent(Object model) {
        return createCodeAgent(model, null, null, null, null, null, null, false, 15,
                null, null, null, null, null, null, null);
    }

    public static DeepAgent createCodeAgent(
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
            String promptMode,
            EmbeddingConfig embeddingConfig
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(CODE_AGENT_FACTORY_NAME, CODE_AGENT_FACTORY_NAME,
                defaultDescription(resolvedLanguage))
                : card;
        List<DeepAgentConfig.SubAgentConfig> effectiveSubagents = injectBuiltinPlanAgents(
                subagents, model, resolvedLanguage);
        List<DeepAgentRail> finalRails = mergeRailsWithRequired(rails, workspace, embeddingConfig, resolvedLanguage);
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
        config.setSubagents(toSubagentMap(effectiveSubagents));
        config.setEnablePlanMode(true);

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

    private static List<DeepAgentConfig.SubAgentConfig> injectBuiltinPlanAgents(
            List<DeepAgentConfig.SubAgentConfig> subagents,
            Object model,
            String language
    ) {
        List<DeepAgentConfig.SubAgentConfig> effective = new ArrayList<>();
        if (subagents != null) {
            subagents.stream().filter(spec -> spec != null).forEach(effective::add);
        }
        if (!hasAgent(effective, ExploreAgent.FACTORY_NAME)) {
            effective.add(ExploreAgent.buildExploreAgentConfig(model, null, null, null, null,
                    language, false, 25));
        }
        if (!hasAgent(effective, PlanAgent.FACTORY_NAME)) {
            effective.add(PlanAgent.buildPlanAgentConfig(model, null, null, null, null, null,
                    language, false, 25));
        }
        return effective;
    }

    private static boolean hasAgent(List<DeepAgentConfig.SubAgentConfig> subagents, String name) {
        if (subagents == null || name == null) {
            return false;
        }
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            if (spec != null && name.equals(spec.getName())) {
                return true;
            }
        }
        return false;
    }

    private static List<DeepAgentRail> mergeRailsWithRequired(
            List<DeepAgentRail> rails,
            Object workspace,
            EmbeddingConfig embeddingConfig,
            String language
    ) {
        List<DeepAgentRail> merged = new ArrayList<>();
        if (rails != null) {
            merged.addAll(rails);
        }
        addRailIfMissing(merged, SysOperationRail.class, new SysOperationRail());
        addRailIfMissing(merged, AgentModeRail.class, new AgentModeRail());
        addRailIfMissing(merged, AskUserRail.class, new AskUserRail());
        addRailIfMissing(merged, ConfirmRail.class, new ConfirmRail());
        if (embeddingConfig != null && merged.stream().noneMatch(CodingMemoryRail.class::isInstance)) {
            merged.add(new CodingMemoryRail(resolveCodingMemoryDir(workspace), embeddingConfig, language));
        }
        return merged;
    }

    private static void addRailIfMissing(
            List<DeepAgentRail> rails,
            Class<? extends DeepAgentRail> railClass,
            DeepAgentRail rail
    ) {
        if (rails.stream().noneMatch(railClass::isInstance)) {
            rails.add(rail);
        }
    }

    private static String resolveCodingMemoryDir(Object workspace) {
        if (workspace instanceof Workspace typedWorkspace) {
            Path nodePath = typedWorkspace.getNodePath(WorkspaceNode.CODING_MEMORY);
            if (nodePath != null) {
                return nodePath.toString();
            }
            return typedWorkspace.root().resolve("coding_memory").toString();
        }
        if (workspace instanceof String path && !path.isBlank()) {
            return Path.of(path).resolve("coding_memory").toString();
        }
        return Path.of("./").resolve("coding_memory").toString();
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
