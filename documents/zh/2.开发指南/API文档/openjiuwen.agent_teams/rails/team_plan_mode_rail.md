# openjiuwen.agent_teams.rails.team_plan_mode_rail

该模块对应 Python `openjiuwen.agent_teams.rails.team_plan_mode_rail`，在 TeamAgent leader 进入 team.plan 模式时替换通用 plan prompt。

## Java 对应

- `com.openjiuwen.agent_teams.rails.TeamPlanModeRail`

## 行为

- `PRIORITY` 为 84，对应 Python `priority = 84`。
- `init(...)` 缓存 agent 和 `SystemPromptBuilder`，并尝试特化内置 `plan_agent`。
- `beforeModelCall(...)` 在 `DeepAgentState.planMode.mode == "plan"` 时添加 `mode_instructions` section。
- 非 plan 模式下移除 `mode_instructions`，保持通用 plan 工具和安全逻辑不被该 rail 覆盖。
- `uninit(...)` 移除 team.plan prompt overlay 并清理缓存引用。

Java 侧当前使用 `PlanModeAgent`、`PlanModeCallbackContext` 和 `DeepConfigView` 作为窄接口，表达 Python 中通过 `Any`、`AgentCallbackContext` 和 `deep_config.subagents` 动态访问的最小行为面；待 `DeepAgentRail` 与 `AgentCallbackContext` 翻译完成后可进一步接入正式 rail base。
