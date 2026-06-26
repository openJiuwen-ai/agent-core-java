# openjiuwen.agent_teams.rails.team_tool_rail

该模块对应 Python `openjiuwen.agent_teams.rails.team_tool_rail`，负责把团队协作工具注册到 agent 的能力管理器，并同步注册到 runner 级资源管理器。

## Java 对应

- `com.openjiuwen.agent_teams.rails.TeamToolRail`

## 行为

- `PRIORITY` 为 90，对应 Python `priority = 90`。
- `init(...)` 幂等；已注册工具时直接返回。
- 通过 `TeamToolFactory` 构造角色相关的团队工具，边界对应 Python `create_team_tools(...)`。
- 当配置 `TeamWorkspaceManager` 时，通过 `ExtensionToolFactory` 追加 `workspace_meta` 类工具。
- 当配置 `WorktreeManager` 时，通过 `ExtensionToolFactory` 追加 worktree 进入/退出工具，并初始化 worktree session state。
- `qualifyTeamToolIds(...)` 按 `toolId.teamName.memberName` 后缀修改工具 ID，对应 Python `qualify_team_tool_ids(...)`。
- `uninit(...)` 从 ability manager 和 resource manager 移除已注册工具；资源管理器异常按 Python 逻辑视为 best-effort 清理失败。

Java 侧当前使用 `TeamToolFactory`、`ExtensionToolFactory`、`ToolView`、`ToolCardView`、`TeamToolAgent`、`AbilityManagerView` 和 `ResourceManagerView` 作为窄接口，表达 Python 动态工具系统和 runner 全局资源管理器的最小行为面；完整团队工具实现由后续 `team_tools.py` 对应任务接入。
