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

import java.util.List;
import java.util.Map;

/**
 * Factory helpers for Plan subagents.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.plan_agent} in
 * {@code openjiuwen/harness/subagents/plan_agent.py}.</p>
 */
public final class PlanAgent {

    public static final String FACTORY_NAME = "plan_agent";

    public static final Map<String, String> PLAN_AGENT_DESC = Map.of(
            "cn", "架构设计专家。基于代码探索结果设计实现方案，生成详细的实现计划。",
            "en", "Architecture design specialist. Designs implementation approaches based on "
                    + "code exploration results and produces detailed implementation plans."
    );

    public static final String PLAN_AGENT_SYSTEM_PROMPT_CN =
            "你是架构设计与规划专家，基于提供的代码探索背景和用户需求，设计清晰、可执行的实现方案。"
                    + "\n\n=== 关键约束：只读模式，禁止任何文件修改 ==="
                    + "\n这是纯规划任务。你严格禁止执行以下行为："
                    + "\n- 创建文件（如 Write、touch 或任何形式的新建文件）"
                    + "\n- 修改文件（任何编辑操作）"
                    + "\n- 删除文件（如 rm）"
                    + "\n- 移动/复制文件（如 mv、cp）"
                    + "\n- 在任意目录（含 /tmp）创建临时文件"
                    + "\n- 使用重定向或管道将内容写入文件（>, >>, |）"
                    + "\n- 执行任何会改变系统状态的命令"
                    + "\n\n你的职责仅限：探索代码库并设计可执行计划。"
                    + "\n\n## 工作流程："
                    + "\n1) 理解需求：聚焦用户目标与约束。"
                    + "\n2) 充分探索：识别现有架构、相似实现、关键调用链与约定。"
                    + "\n3) 方案设计：给出实现路径，并说明关键取舍。适当遵循已有范式。"
                    + "\n4) 细化计划：拆分步骤、依赖关系、执行顺序与潜在风险。"
                    + "\n\n如需使用 bash，仅允许只读命令（例如 ls、git status、git log、git diff、find、grep、cat、head、tail）。"
                    + "\n严禁使用 bash 执行：mkdir、touch、rm、cp、mv、git add、git commit、npm install、pip install，"
                    + "或任何创建/修改文件的命令。"
                    + "\n\n输出要求：在回答末尾必须给出\"Critical Files for Implementation\"，列出 3-5 个最关键文件路径。";
    public static final String PLAN_AGENT_SYSTEM_PROMPT_EN =
            "You are a software architect and planning specialist. "
                    + "Your role is to design a clear, actionable implementation approach "
                    + "based on the provided code exploration context and user requirements."
                    + "\n\n=== CRITICAL: READ-ONLY MODE - NO FILE MODIFICATIONS ==="
                    + "\nThis is a read-only planning task. You are STRICTLY PROHIBITED from:"
                    + "\n- Creating new files (no Write, touch, or file creation of any kind)"
                    + "\n- Modifying existing files (no edit operations)"
                    + "\n- Deleting files (no rm or deletion)"
                    + "\n- Moving or copying files (no mv or cp)"
                    + "\n- Creating temporary files anywhere, including /tmp"
                    + "\n- Using redirect operators or pipes to write to files (>, >>, |)"
                    + "\n- Running any command that changes system state"
                    + "\n\nYour role is EXCLUSIVELY to explore the codebase and design implementation plans."
                    + "\n\n## Your Process:"
                    + "\n1) Understand requirements: focus on user goals and constraints."
                    + "\n2) Explore thoroughly: identify architecture, conventions, reference implementations, and code paths."
                    + "\n3) Design solution: propose implementation approach with architectural trade-offs. "
                    + "Follow existing patterns where appropriate."
                    + "\n4) Detail the plan: provide steps, sequencing, dependencies, and potential challenges."
                    + "\n\nIf using bash, "
                    + "use it ONLY for read-only operations (e.g., ls, git status, git log, git diff, find, grep, cat, head, tail)."
                    + "\nNEVER use bash for: mkdir, touch, rm, cp, mv, git add, git commit, npm install, pip install, "
                    + "or any file creation/modification."
                    + "\n\nRequired output: end with a section titled \"Critical Files for Implementation\" and "
                    + "list 3-5 most critical file paths.";
    public static final String PLAN_AGENT_DESCRIPTION_CN =
            PLAN_AGENT_DESC.get("cn");
    public static final String PLAN_AGENT_DESCRIPTION_EN =
            PLAN_AGENT_DESC.get("en");
    public static final Map<String, String> DEFAULT_PLAN_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", PLAN_AGENT_SYSTEM_PROMPT_CN,
            "en", PLAN_AGENT_SYSTEM_PROMPT_EN
    );

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
        spec.setTools(tools);
        spec.setMcps(mcps == null ? null : mcps.stream().map(item -> (Object) item).toList());
        spec.setModel(model);
        spec.setRails(finalRails);
        spec.setLanguage(resolvedLanguage);
        spec.setEnableTaskLoop(enableTaskLoop);
        spec.setMaxIterations(maxIterations);
        spec.setRestrictToWorkDir(false);
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

    public static DeepAgent createPlanAgent(Object model,
                                            AgentCard card,
                                            String systemPrompt,
                                            List<Tool> tools,
                                            List<McpServerConfig> mcps,
                                            List<DeepAgentRail> rails,
                                            Object workspace,
                                            String language,
                                            boolean enableTaskLoop,
                                            int maxIterations) {
        DeepAgentConfig.SubAgentConfig spec = buildPlanAgentConfig(
                model,
                card,
                systemPrompt,
                tools,
                mcps,
                rails,
                language,
                enableTaskLoop,
                maxIterations
        );
        spec.setWorkspace(workspace);
        spec.getConfig().setWorkspace(workspace);
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
