# openjiuwen.agent_teams.runtime.manager

Java 对应类型：

- `com.openjiuwen.agent_teams.runtime.TeamRuntimeActivation`
- `com.openjiuwen.agent_teams.runtime.TeamSessionReleaseInfo`
- `com.openjiuwen.agent_teams.runtime.TeamRuntimeManager`

Python 源文件：`openjiuwen/agent_teams/runtime/manager.py`

## 职责

`TeamRuntimeManager` 持有进程内 active team pool，并在 team run、interact、pause、stop、finalize、release 和 delete 入口处执行生命周期决策。

## Java 边界

当前 Java 翻译保留 manager 的核心职责，并用窄接口隔离尚未完全翻译的运行时依赖：

- `TeamAgentRuntime` 表达 manager 对 TeamAgent 的调用面
- `TeamBackendRuntime` / `TeamMessageManagerRuntime` 表达交互投递所需的 backend 和 message manager 调用面
- `AgentTeamSessionView` 表达 session pre-run、flush 和 session id
- `TeamSpecView` 表达 build/recover 入口
- `SessionInspector` 表达 `_inspect_session`
- `RuntimeCleanup` 表达 release/delete 的外部 DB、checkpoint 和 filesystem cleanup

## 覆盖行为

- `activate` 根据 `TeamRunDispatcher` 决策创建、恢复、resume 或拒绝
- stale pool entry 在 activate 前 stop/remove
- `finalizeTeam` 对 persistent team pause，对 temporary/shutdown-requested team stop
- `finalizeMember` 保留 Python 的 terminal status guard 和 shutdown-requested 分支
- `pause` / `stopTeam` 只处理精确匹配的 `(team_name, session_id)`
- `interact` 复用现有 `InteractionRouter`、`UserInbox` 和 `HumanAgentInbox`
- `releaseSession` / `deleteTeam` 保留 busy guard 和 `force=True` 停止 active runtime 语义
- `getMonitor` 通过注入的 factory 返回 active runtime monitor
