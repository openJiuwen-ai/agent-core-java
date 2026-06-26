# openjiuwen.agent_teams.runtime.pool

Java 对应包：`com.openjiuwen.agent_teams.runtime`

该模块提供进程内 TeamAgent 运行时对象池。池以 `teamName` 作为唯一键，一个团队同一时间最多绑定一个内存运行时；多个团队可以共享同一个 session id。

## RuntimeState

`RuntimeState` 表示池内团队的顶层运行状态：

- `RUNNING`：对应 Python 字符串值 `running`
- `PAUSED`：对应 Python 字符串值 `paused`

## ActiveTeam

`ActiveTeam` 是池内的可变运行时条目，字段包括：

- `teamName()`：团队名，也是池键
- `agent()`：当前持有的 `TeamAgent`
- `currentSessionId()`：当前绑定的 session id
- `state()` / `setState(...)`：运行状态，缺省为 `RuntimeState.RUNNING`
- `interactGate()`：该团队独占的 `InteractGate`

## ActiveTeamInfo

`ActiveTeamInfo` 是对外观察用的只读快照。它不暴露 live `TeamAgent` 和 `InteractGate` 引用，只保留：

- `teamName`
- `currentSessionId`
- `state`
- `gateClosed`

## TeamRuntimePool

`TeamRuntimePool` 使用同步的插入有序映射保存 `ActiveTeam`：

- `get(teamName)`：返回团队条目，不存在时返回 `null`
- `hasActive(teamName)`：检查团队是否在池内
- `add(entry)`：按团队名注册条目，同名时替换旧条目
- `remove(teamName)`：移除并返回旧条目，不存在时返回 `null`
- `listTeamNames()`：返回当前团队名快照，保持插入顺序
- `teamsForSession(sessionId)`：返回绑定到指定 session 的条目列表
- `listAllInfo()`：返回所有团队的只读 `ActiveTeamInfo` 快照

## Python 对应关系

该 Java 实现对应 Python 源文件：

`openjiuwen/agent_teams/runtime/pool.py`
