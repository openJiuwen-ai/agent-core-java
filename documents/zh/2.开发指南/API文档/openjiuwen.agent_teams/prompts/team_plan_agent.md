# openjiuwen.agent_teams.prompts.team_plan_agent

`TeamPlanAgent` 对应 Python `team_plan_agent.py`，用于把内置 `plan_agent` 从通用代码规划提示词替换为 team.plan 专用提示词。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.TeamPlanAgent`
- `TeamPlanAgent.PlanSubAgentConfig`

## 常量

- `TEAM_PLAN_AGENT_DESC`：中英文 team.plan plan-agent 描述。
- `TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN` / `TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN`：从 `team_plan_agent.md` 模板加载的中英文系统 prompt。
- `DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT`：按语言索引的默认 team.plan prompt。

## 方法

- `teamPlanAgentDescription(String language)`：按语言返回 plan-agent 描述，未知语言回退中文。
- `teamPlanAgentPrompt(String language)`：按语言返回 team.plan prompt，未知语言回退中文。
- `applyTeamPlanAgentPrompt(Collection<?> subagents, String language)`：只在目标为 `plan_agent` 且系统 prompt 仍是内置默认 code-plan prompt 时替换 prompt 和描述；用户自定义 prompt 保持不变。
- `buildTeamPlanAgentCard(String language)`：构造默认 `plan_agent` 卡片，便于测试和定制。

`Collection<?>` 入参对齐 Python `Optional[list[Any]]` 的动态边界；实际可更新对象是具体的 `PlanSubAgentConfig`。
