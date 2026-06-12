# openjiuwen.agent_teams.memory

对应 Python 文件：`openjiuwen/agent_teams/memory/__init__.py`

`MemoryPackage` 表示 agent-team memory 包的 Java facade，承载 Python 包级常量和 `__all__` 导出清单。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.MemoryPackage`

主要行为：

- `TEAM_MEMORY_FILENAME` 对应 Python 常量 `"TEAM_MEMORY.md"`。
- `TEAM_MEMORY_MAX_READ_LINES` 对应 Python 常量 `200`。
- `EXPORTED_SYMBOLS` 保持与 Python `__all__` 顺序一致。
- 已存在的 `TeamMemoryConfig` 通过 `TEAM_MEMORY_CONFIG` 暴露。
- `resolveEmbeddingConfig(config)` 透传到 `TeamMemoryConfig.resolveEmbeddingConfig(config)`。

实现说明：

- `TeamMemoryManager`、`TeamMemoryManagerParams`、`MemberMemoryToolkit` 和相关枚举属于后续 Python 文件任务，本 facade 只记录导出名，不提前扩大翻译范围。
