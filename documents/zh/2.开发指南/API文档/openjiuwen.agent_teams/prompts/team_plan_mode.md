# openjiuwen.agent_teams.prompts.team_plan_mode

`TeamPlanMode` 对应 Python `team_plan_mode.py`，负责构造 team.plan leader 的 `MODE_INSTRUCTIONS` prompt section。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.TeamPlanMode`
- `TeamPlanMode.PlanFileProvider`
- `TeamPlanMode.PlanSession`

## 常量

- `TEAM_PLAN_MODE_PROMPT_CN` / `TEAM_PLAN_MODE_PROMPT_EN`：从 `team_plan_mode.md` 模板加载的中英文 team.plan prompt。

## 方法

- `buildTeamPlanModePromptTemplate(String language)`：按解析后的语言加载模板。
- `getTeamPlanModePrompt(String language)`：返回中英文模板，非英文回退中文。
- `buildEnterPlanModeStatus(...)`：根据是否已有 plan 文件路径生成首步状态提示。
- `buildPlanFileInfo(...)`：根据 plan 文件路径是否为空、文件是否存在，生成可读写提示。
- `buildTeamPlanModePrompt(...)`：替换模板中的 `enter_plan_mode_status` 与 `plan_file_info`。
- `buildTeamPlanModeSection(...)`：构造 `SectionName.MODE_INSTRUCTIONS`，优先级为 85。

`PlanFileProvider` 是对 Python `agent.get_plan_file_path(session)` 的窄接口抽象；完整 DeepAgent/Session 生命周期由后续对应模块承载。
