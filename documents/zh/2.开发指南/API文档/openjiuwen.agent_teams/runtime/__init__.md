# openjiuwen.agent_teams.runtime

Java 对应类型：`com.openjiuwen.agent_teams.runtime.TeamRuntimePackage`

Python 源文件：`openjiuwen/agent_teams/runtime/__init__.py`

## 职责

该模块是 `agent_teams.runtime` 包的导出入口，对应 Python `__all__`。

## 导出符号

- `ActiveTeam`
- `ActiveTeamInfo`
- `RunAction`
- `RunActionKind`
- `RuntimeState`
- `TeamRuntimeActivation`
- `TeamRuntimeManager`
- `TeamRuntimePool`
- `TeamSessionReleaseInfo`

## Java 说明

当前任务只翻译 `__init__.py` 的包导出元数据，不提前实现 `dispatch.py`、`manager.py` 或 `pool.py` 的运行时类型。Java facade 使用字符串导出表保持可追溯性，避免引用尚未翻译的类。
