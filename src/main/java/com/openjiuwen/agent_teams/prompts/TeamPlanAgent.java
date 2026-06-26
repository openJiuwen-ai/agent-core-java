/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Team.plan specialization for the built-in plan subagent.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/prompts/team_plan_agent.py}.</p>
 */
public final class TeamPlanAgent {

    public static final Map<String, String> TEAM_PLAN_AGENT_DESC = Map.of(
            "cn", "团队规划专家。基于目标、约束和上下文设计团队执行方案、分工、依赖和验收计划。",
            "en", "Team planning specialist. Designs team execution strategy, role split, "
                    + "dependencies, and acceptance plans from goals, constraints, and context."
    );

    public static final String TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN =
            String.valueOf(PromptLoader.loadTemplate("team_plan_agent", "cn").getContent()).strip();
    public static final String TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN =
            String.valueOf(PromptLoader.loadTemplate("team_plan_agent", "en").getContent()).strip();

    public static final Map<String, String> DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN,
            "en", TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN
    );

    static final String PLAN_AGENT_SYSTEM_PROMPT_CN = "你是架构设计与规划专家，基于提供的代码探索背景和用户需求，设计清晰、可执行的实现方案。"
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
            + "\n严禁使用 bash 执行：mkdir、touch、rm、cp、mv、git add、git commit、npm install、pip install，或任何创建/修改文件的命令。"
            + "\n\n输出要求：在回答末尾必须给出\"Critical Files for Implementation\"，列出 3-5 个最关键文件路径。";

    static final String PLAN_AGENT_SYSTEM_PROMPT_EN = "You are a software architect and planning specialist. "
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

    static final Map<String, String> DEFAULT_PLAN_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", PLAN_AGENT_SYSTEM_PROMPT_CN,
            "en", PLAN_AGENT_SYSTEM_PROMPT_EN
    );

    private TeamPlanAgent() {
    }

    public static String teamPlanAgentDescription(String language) {
        return TEAM_PLAN_AGENT_DESC.getOrDefault(language, TEAM_PLAN_AGENT_DESC.get("cn"));
    }

    public static String teamPlanAgentPrompt(String language) {
        return DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT.getOrDefault(
                language,
                DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT.get("cn")
        );
    }

    public static boolean applyTeamPlanAgentPrompt(Collection<?> subagents, String language) {
        if (subagents == null || subagents.isEmpty()) {
            return false;
        }
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        Set<String> builtinPrompts = Set.copyOf(DEFAULT_PLAN_AGENT_SYSTEM_PROMPT.values());

        for (Object candidate : subagents) {
            if (!(candidate instanceof PlanSubAgentConfig spec)) {
                continue;
            }
            AgentCard card = spec.getAgentCard();
            if (card == null || !"plan_agent".equals(card.getName())) {
                continue;
            }
            if (!builtinPrompts.contains(spec.getSystemPrompt())) {
                return false;
            }
            spec.setSystemPrompt(teamPlanAgentPrompt(resolvedLanguage));
            spec.setAgentCard(copyCardWithDescription(card, teamPlanAgentDescription(resolvedLanguage)));
            return true;
        }
        return false;
    }

    public static AgentCard buildTeamPlanAgentCard(String language) {
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        return new AgentCard(null, "plan_agent", teamPlanAgentDescription(resolvedLanguage));
    }

    private static AgentCard copyCardWithDescription(AgentCard source, String description) {
        return new AgentCard(source.getId(), source.getName(), description);
    }

    /**
     * Minimal subagent prompt carrier for the team plan-agent specialization.
     *
     * <p>Mirrors Python's {@code SubAgentConfig} use in
     * {@code openjiuwen/agent_teams/prompts/team_plan_agent.py}.</p>
     */
    public static class PlanSubAgentConfig {
        private AgentCard agentCard;
        private String systemPrompt;

        public PlanSubAgentConfig(AgentCard agentCard, String systemPrompt) {
            this.agentCard = Objects.requireNonNull(agentCard, "agentCard");
            this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
        }

        public AgentCard getAgentCard() {
            return agentCard;
        }

        public void setAgentCard(AgentCard agentCard) {
            this.agentCard = agentCard;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}
