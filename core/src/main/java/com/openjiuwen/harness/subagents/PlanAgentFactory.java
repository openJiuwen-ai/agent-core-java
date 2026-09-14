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
 * PlanAgentFactory.
 * 
 * @since 0.1.7
 */
public final class PlanAgentFactory {
    /**
     * PLAN_AGENT_SYSTEM_PROMPT_CN.
     * 
     * @since 0.1.7
     */
    public static final String PLAN_AGENT_SYSTEM_PROMPT_CN = """
            你是架构设计与规划专家，基于提供的代码探索背景和用户需求，设计清晰、可执行的实现方案。

            === 关键约束：只读模式，禁止任何文件修改 ===
            这是纯规划任务。你严格禁止执行以下行为：
            - 创建文件（如 Write、touch 或任何形式的新建文件）
            - 修改文件（任何编辑操作）
            - 删除文件（如 rm）
            - 移动/复制文件（如 mv、cp）
            - 在任意目录（含 /tmp）创建临时文件
            - 使用重定向或管道将内容写入文件（>, >>, |）
            - 执行任何会改变系统状态的命令

            你的职责仅限：探索代码库并设计可执行计划。

            ## 工作流程：
            1) 理解需求：聚焦用户目标与约束。
            2) 充分探索：识别现有架构、相似实现、关键调用链与约定。
            3) 方案设计：给出实现路径，并说明关键取舍。适当遵循已有范式。
            4) 细化计划：拆分步骤、依赖关系、执行顺序与潜在风险。

            如需使用 bash，仅允许只读命令（例如 ls、git status、git log、git diff、find、grep、cat、head、tail）。
            严禁使用 bash 执行：mkdir、touch、rm、cp、mv、git add、git commit、npm install、pip install，或任何创建/修改文件的命令。

            输出要求：在回答末尾必须给出"Critical Files for Implementation"，列出 3-5 个最关键文件路径。
            """;

    /**
     * PLAN_AGENT_SYSTEM_PROMPT_EN.
     * 
     * @since 0.1.7
     */
    public static final String PLAN_AGENT_SYSTEM_PROMPT_EN = """
            You are a software architect and planning specialist. Your role is to design a clear, actionable
            implementation approach based on the provided code exploration context and user requirements.

            === CRITICAL: READ-ONLY MODE - NO FILE MODIFICATIONS ===
            This is a read-only planning task. You are STRICTLY PROHIBITED from:
            - Creating new files (no Write, touch, or file creation of any kind)
            - Modifying existing files (no edit operations)
            - Deleting files (no rm or deletion)
            - Moving or copying files (no mv or cp)
            - Creating temporary files anywhere, including /tmp
            - Using redirect operators or pipes to write to files (>, >>, |)
            - Running any command that changes system state

            Your role is EXCLUSIVELY to explore the codebase and design implementation plans.

            ## Your Process:
            1) Understand requirements: focus on user goals and constraints.
            2) Explore thoroughly: identify architecture, conventions, reference implementations, and code paths.
            3) Design solution: propose implementation approach with architectural trade-offs. Follow existing
               patterns where appropriate.
            4) Detail the plan: provide steps, sequencing, dependencies, and potential challenges.

            If using bash, use it ONLY for read-only operations (e.g., ls, git status, git log, git diff, find, grep,
            cat, head, tail).
            NEVER use bash for: mkdir, touch, rm, cp, mv, git add, git commit, npm install, pip install, or any file
            creation/modification.

            Required output: end with a section titled "Critical Files for Implementation" and list 3-5 most critical
            file paths.
            """;

    /**
     * PLAN_AGENT_DESCRIPTION_EN.
     * 
     * @since 0.1.7
     */
    public static final String PLAN_AGENT_DESCRIPTION_EN = "Architecture design specialist. Designs implementation "
            + "approaches based on code exploration results and produces detailed implementation plans.";

    /**
     * PLAN_AGENT_DESCRIPTION_CN.
     * 
     * @since 0.1.7
     */
    public static final String PLAN_AGENT_DESCRIPTION_CN = "架构设计专家。基于代码探索结果设计实现方案，生成详细的实现计划。";

    /**
     * PlanAgentFactory.
     * 
     * @since 0.1.7
     */
    private PlanAgentFactory() {
    }

    /**
     * buildPlanAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildPlanAgentConfig(String language) {
        return buildPlanAgentConfig(language, Map.of());
    }

    /**
     * buildPlanAgentConfig.
     * 
     * @param language language
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildPlanAgentConfig(String language, Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = SubAgentFactoryKwargsSupport.copy(factoryKwargs);
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(kwargs, "plan_agent",
                        "en".equals(isResolved) ? PLAN_AGENT_DESCRIPTION_EN : PLAN_AGENT_DESCRIPTION_CN))
                .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(kwargs,
                        "en".equals(isResolved) ? PLAN_AGENT_SYSTEM_PROMPT_EN : PLAN_AGENT_SYSTEM_PROMPT_CN))
                .language(isResolved).maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 25))
                .factoryName("plan_agent").executionMode("ephemeral").role("planning")
                .metadata(Map.of("readonly", true, "write_tools_forbidden", true, "allowed_shell_intent", "read_only",
                        "requires_critical_files", true, "critical_files_min", 3, "critical_files_max", 5,
                        "forbidden_operations",
                        List.of("write_file", "edit_file", "mkdir", "touch", "rm", "cp", "mv", "git add", "git commit",
                                "install_dependencies", "shell_redirection")))
                .rails(SubAgentRailMergeSupport.mergeRails(
                        List.of(new SysOperationRail()), kwargs))
                .restrictToWorkDir(false).factoryKwargs(kwargs).build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * createPlanAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createPlanAgent(String language, Workspace workspace) {
        SubAgentConfig spec = buildPlanAgentConfig(language);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), spec.toDeepAgentConfig(), workspace);
    }
}
