# openjiuwen.agent_teams.runtime.dispatch

Java 对应类型：

- `com.openjiuwen.agent_teams.runtime.RunActionKind`
- `com.openjiuwen.agent_teams.runtime.RunAction`
- `com.openjiuwen.agent_teams.runtime.TeamRunDispatcher`

Python 源文件：`openjiuwen/agent_teams/runtime/dispatch.py`

## 职责

`dispatch.py` 是 `run_agent_team_streaming` 的纯决策层。它根据 DB、session checkpoint、runtime pool 和生命周期状态选择创建、冷恢复、恢复暂停、拒绝运行中、拒绝孤儿状态或拒绝不一致状态。

## 行为对应

- `RunActionKind` 保留 Python enum 的字符串值
- `RunAction` 保留 `kind`、`require_spec` 和 `reason`
- `TeamRunDispatcher.decideRunAction(...)` 对应 `decide_run_action(...)`
- `pending_create` 和 `cleaned` 状态在 DB 缺失但 session bucket 存在时允许重新创建
- DB 缺失但 pool 存在时拒绝不一致状态
- DB 存在、pool 缺失时根据 session bucket 选择 `new_team_in_session` 或 `cold_recover`
- 同 session paused pool entry 选择 `resume_from_pause`
- 同 session running pool entry 选择 `reject_running`
- 跨 session pool entry 抛出 invariant 异常

## 依赖边界

当前任务只翻译 `dispatch.py`。Python 的 `ActiveTeam` 和 `RuntimeState` 定义在 `pool.py`，属于独立任务。Java 侧因此使用 `PoolEntryView` 暴露当前决策函数实际读取的两个字段：`currentSessionId` 和 `state`，避免提前实现 pool 运行时。
