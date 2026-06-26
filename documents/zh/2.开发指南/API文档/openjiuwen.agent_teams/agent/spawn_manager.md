# openjiuwen.agent_teams.agent.spawn_manager

## SpawnManager

`SpawnManager` 管理 TeamAgent teammate 的进程和 in-process 生命周期，对应 Python 源文件 `openjiuwen/agent_teams/agent/spawn_manager.py`。

Java 侧覆盖的核心行为：

- `spawnTeammate` 对已经存在或正在启动的 member 做幂等保护。
- 根据 `cliAgent` 和 `spawnMode` 选择 external CLI、in-process 或普通 process spawn 分支。
- in-process teammate 会把 chunk observer 接到 leader 的 stream queue，并在 cleanup 时移除。
- `cleanupTeammate` 停止 health check，并在句柄仍存活时强制结束。
- `restartTeammate` 先清理旧句柄，再从后端重建 `TeamRuntimeContext` 并重试启动。
- `onTeammateUnhealthy` 标记 teammate 为 restarting 后触发 restart。
- `shutdownAllHandles` 统一走 `cleanupTeammate`，避免 in-process observer 泄漏。
- `cancelRecoveryTasks` 取消并清空 recovery task 集合。

`SpawnExecutor`、`SpawnHandle`、`TeamBackendView` 等是本翻译单元使用的窄接口，用于承接 Runner、in-process spawn、external CLI spawn 和 team backend 的最小访问面。
