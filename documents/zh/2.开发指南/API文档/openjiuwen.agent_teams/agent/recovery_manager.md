# openjiuwen.agent_teams.agent.RecoveryManager

`RecoveryManager` 对应 Python 模块 `openjiuwen/agent_teams/agent/recovery_manager.py`，负责团队恢复、session 切换时 teammate 重启、leader 配置持久化和 allocator 状态持久化。

## 主要能力

- `recoverTeam`：当 team backend 缺失时返回空列表；存在 backend 时跳过 leader，将其它成员标记为 `restarting`，并调用 spawn manager 重启 teammate。
- `persistLeaderConfig`：将 `spec`、`context`、team DB state 和可选 allocator state 写入 team namespace；缺少 spec、context 或 team name 时不写入。
- `markTeammateRestartingForSessionSwitch`：`ready`、`busy`、`shutdown_requested`、`unstarted` 等 active 状态先归一到 `error`，再进入 `restarting`；`paused`、`stopped`、`error`、`shut_down` 可直接进入 `restarting`。
- `collectLiveTeammatesForSessionSwitch`：只在 leader 角色收集有 live spawned handle 且状态不是 `unstarted`、`shut_down`、`stopped` 的 teammate。
- `restartForSessionSwitch`：可选先 cleanup 旧 handle，然后标记 `restarting` 并重启 teammate。
- `persistAllocatorState`：把 allocator 的 `stateDict` 合并到 team namespace；写入失败时记录日志并吞掉异常。

## 依赖边界

当前 Java 项目尚未翻译完整 `SpawnManager` 和 Python team backend facade。Java 实现使用窄接口 `SpawnManagerPort`、`MemberRegistry` 和 `StatefulAllocator` 固定本模块实际依赖，后续完整门面翻译完成后可通过适配器接入。
