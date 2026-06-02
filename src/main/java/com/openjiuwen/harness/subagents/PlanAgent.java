/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;

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

    private static final String SYSTEM_PROMPT_CN =
            "你是架构设计与规划专家，基于提供的代码探索背景和用户需求，设计清晰、可执行的实现方案。\n\n"
                    + "=== 关键约束：只读模式，禁止任何文件修改 ===\n"
                    + "这是纯规划任务。你严格禁止创建、修改、删除、移动、复制文件，"
                    + "也禁止使用重定向或任何会改变系统状态的命令。\n\n"
                    + "你的职责仅限于：理解需求、探索代码、设计方案、拆解步骤、说明依赖与风险。\n\n"
                    + "如需使用 bash，仅允许只读操作，例如 ls、git status、git log、git diff、find、grep、cat、head、tail。\n\n"
                    + "Critical Files for Implementation";
    private static final String SYSTEM_PROMPT_EN =
            "You are a software architect and planning specialist. "
                    + "Design a clear, actionable implementation approach based on the provided code exploration context and user requirements.\n\n"
                    + "=== CRITICAL: READ-ONLY MODE - NO FILE MODIFICATIONS ===\n"
                    + "This is a read-only planning task. Do not create, modify, delete, move, or copy files. "
                    + "Do not use redirects or commands that change system state.\n\n"
                    + "Your job is to understand requirements, explore the codebase, design the solution, "
                    + "and detail execution steps, dependencies, and risks.\n\n"
                    + "If you use bash, keep it read-only.\n\n"
                    + "Critical Files for Implementation";

    private static final String DESCRIPTION_CN = "架构设计专家。基于代码探索结果设计实现方案，并产出详细实施计划。";
    private static final String DESCRIPTION_EN =
            "Architecture design specialist. Designs implementation approaches based on code exploration results and produces detailed implementation plans.";

    public static String getSystemPrompt(String language) {
        return "en".equalsIgnoreCase(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equalsIgnoreCase(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }

    public static DeepAgentConfig buildPlanAgentConfig(String language) {
        return buildPlanAgentConfig(null, null, null, null, null, false, 25, null, null, null, language);
    }

    public static DeepAgentConfig buildPlanAgentConfig(
            Model model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<AgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            List<McpServerConfig> mcps,
            Object workspace,
            SysOperation sysOperation,
            String language
    ) {
        String resolvedLanguage = resolveLanguage(language);
        AgentCard effectiveCard = card != null ? card : AgentCard.builder()
                .name(FACTORY_NAME)
                .description(getDescription(resolvedLanguage))
                .build();

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(effectiveCard);
        config.setSystemPrompt(systemPrompt != null ? systemPrompt : getSystemPrompt(resolvedLanguage));
        config.setRails(resolveRails(rails));
        config.setEnableTaskLoop(enableTaskLoop);
        config.setMaxIterations(maxIterations);
        config.setTools(toToolCards(tools));
        config.setMcps(mcps != null ? mcps : List.of());
        config.setWorkspace(resolveWorkspace(workspace, resolvedLanguage));
        config.setSysOperation(sysOperation);
        config.setModel(model);
        assignModelConfig(config, model);
        return config;
    }

    public static DeepAgent createPlanAgent(Model model, Object workspace, String language) {
        return createPlanAgent(model, null, null, null, null, workspace, null, language);
    }

    public static DeepAgent createPlanAgent(
            Model model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<AgentRail> rails,
            Object workspace,
            SysOperation sysOperation,
            String language
    ) {
        DeepAgentConfig config = buildPlanAgentConfig(
                model,
                card,
                systemPrompt,
                tools,
                rails,
                false,
                25,
                null,
                workspace,
                sysOperation,
                language
        );
        return HarnessFactory.createDeepAgent(config);
    }

    private static List<AgentRail> resolveRails(List<AgentRail> rails) {
        if (rails != null) {
            return new ArrayList<>(rails);
        }
        return new ArrayList<>(List.of(new SysOperationRail()));
    }

    private static List<ToolCard> toToolCards(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().map(Tool::getCard).toList();
    }

    private static Workspace resolveWorkspace(Object workspace, String language) {
        if (workspace instanceof Workspace typed) {
            return typed;
        }
        if (workspace instanceof String path) {
            return new Workspace(path, language);
        }
        return null;
    }

    private static void assignModelConfig(DeepAgentConfig config, Model model) {
        if (config == null || model == null) {
            return;
        }
        ModelClientConfig clientConfig = readField(model, "modelClientConfig", ModelClientConfig.class);
        ModelRequestConfig requestConfig = readField(model, "modelConfig", ModelRequestConfig.class);
        config.setModelClientConfig(clientConfig);
        config.setModelRequestConfig(requestConfig);
    }

    private static String resolveLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "cn";
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return type.isInstance(value) ? (T) value : null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }
}
