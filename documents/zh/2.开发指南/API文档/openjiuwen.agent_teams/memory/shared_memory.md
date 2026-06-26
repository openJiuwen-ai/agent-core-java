# openjiuwen.agent_teams.memory.shared_memory

对应 Python 文件：`openjiuwen/agent_teams/memory/shared_memory.py`

`SharedMemoryManager` 管理团队级 `TEAM_MEMORY.md` 文件。成员通过读取团队摘要获得共享记忆，leader 的提取流程可以覆盖写入或追加条目。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.SharedMemoryManager`

常量：

- `TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md"`
- `TEAM_MEMORY_MAX_READ_LINES = 200`

主要行为：

- `ensureDir()`：创建 team-memory 目录。
- `readTeamSummaryText()`：读取 `TEAM_MEMORY.md`，最多返回前 200 行，缺失、空文件或读取错误时返回空字符串。
- `readTeamSummary()`：供 `TeamMemoryManager` prompt 注入使用，空内容返回 `Optional.empty()`。
- `writeTeamSummary(content)`：覆盖写入整个 `TEAM_MEMORY.md`；优先使用 `sysOperation.writeFile`，失败后回退本地原子替换。
- `appendEntry(entry)`：先读现有摘要，再用 `\n\n---\n\n` 追加新条目，最后覆盖写回。
- `targetPath()`：返回当前团队摘要文件路径。

实现说明：

- 本地写入使用临时文件加 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；文件系统不支持原子 move 时回退为普通 replace。
- 与 Python 一样，append 是读-改-写流程，不声明并发原子性。
- `TeamMemoryManager` 默认 shared-memory factory 已刷新为构造真实 `SharedMemoryManager`。
