# openjiuwen.agent_teams.memory.manager

对应 Python 文件：`openjiuwen/agent_teams/memory/manager.py`

`TeamMemoryManager` 负责在 TeamAgent 生命周期中协调成员记忆工具、系统 prompt 注入、团队共享记忆读取、轮次结束后的团队记忆提取，以及停止协作时的资源清理。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.TeamMemoryManager`
- `TeamMemoryManager.Parameters`
- `TeamMemoryManager.MemberMemoryToolkitView`
- `TeamMemoryManager.SharedMemoryManagerView`
- `TeamMemoryManager.DeepAgentView`
- `TeamMemoryManager.ExtractionInvoker`

主要行为：

- `initToolkit()`：按成员、团队、workspace、scenario、embedding 配置初始化成员记忆 toolkit；存在 `teamMemoryDir` 时初始化 shared memory manager。
- `registerTools(deepAgent)`：先移除 DeepAgent 上旧的 memory/coding memory rail，再把 toolkit tools 注册到 resource manager 和 ability manager，并记录本 manager 拥有的 tool id/name。
- `loadAndInject(deepAgent, query)`：构造并缓存基础 memory/coding-memory prompt section；每轮注入时复制缓存内容，追加个人相关记忆和团队共享记忆，然后替换 `team_memory` section。
- `extractAfterRound()`：仅在 `enableAutoExtract=true`、`lifecycle=persistent`、`role=leader`、存在 `teamMemoryDir` 和 `database` 时调用团队记忆提取边界。
- `close()`：移除 prompt section、ability、resource manager tools，关闭 toolkit，并清空缓存状态；多次调用保持幂等。

实现说明：

- Python 中动态导入的 `MemberMemoryToolkit`、`SharedMemoryManager`、`extract_team_memories` 在 Java 中以 typed interface/factory 注入，避免提前扩大到后续 `member_memory_toolkit.py`、`shared_memory.py`、`manager_params.py` 的翻译范围。
- prompt section 使用现有 `MemorySection` / `CodingMemorySection` 构造后重命名为 Python manager 的 `team_memory` section。
- 个人记忆检索保留 Python 规则：coding scenario 使用 `coding_memory`，普通场景使用 `memory`；搜索结果排除 `MEMORY.md`，最多读取 5 条，并限制相关记忆内容总量为 10 KiB。
