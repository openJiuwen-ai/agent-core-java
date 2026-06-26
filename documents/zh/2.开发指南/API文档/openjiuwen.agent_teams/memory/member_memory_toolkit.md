# openjiuwen.agent_teams.memory.member_memory_toolkit

对应 Python 文件：`openjiuwen/agent_teams/memory/member_memory_toolkit.py`

`MemberMemoryToolkit` 为团队成员创建个人记忆工具集合。它根据 scenario 选择普通 memory 或 coding_memory 节点，初始化记忆索引 manager，构造工具上下文，并按 read-only 模式裁剪写入/编辑工具。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.MemberMemoryToolkit`
- `MemberMemoryToolkit.MemoryManagerParams`
- `MemberMemoryToolkit.MemoryToolContext`
- `MemberMemoryToolkit.CodingMemoryToolContext`
- `MemberMemoryToolkit.MemoryLocalFunction`
- `MemberMemoryToolkit.InputSchema`
- `MemberMemoryToolkit.ToolParameter`

主要行为：

- `initialize()`：如果已经初始化且 manager 未关闭，直接返回成功；memory 被禁用时返回失败；否则构造 `MemoryManagerParams` 并解析 manager、context 和 tools。
- `getTools()`：返回当前工具列表副本。
- `getToolCards()`：返回工具 card 列表。
- `manager()` / `getManager()`：返回当前记忆索引 manager。
- `close()`：关闭 manager，清空 context/tools，并将 initialized 置回 false。
- `createGeneralTools(toolkit, readOnly)`：创建 `memory_search`、`memory_get`、`read_memory`，非只读时追加 `write_memory`、`edit_memory`。
- `createCodingTools(toolkit, readOnly)`：创建 `coding_memory_read`，非只读时追加 `coding_memory_write`、`coding_memory_edit`。

工具 id 规则：

- 普通 memory：`memory.<teamName>.<memberName>.<toolName>`
- coding memory：`coding_memory.<teamName>.<memberName>.<toolName>`

实现说明：

- Java 使用 `TeamMemoryManager.ToolCard` / `ToolView` 作为工具注册边界，和 `TeamMemoryManager` 的注册逻辑直接衔接。
- Python 的 `input_params` dict 被翻译为 typed `InputSchema` / `ToolParameter`，保留字段名、类型、描述、required 列表和 `append=false` 默认值。
- 底层 `MemoryIndexManager.get` 被表示为 typed `MemoryIndexManagerProvider`，便于后续接入真实 manager，同时 focused tests 可以隔离 sqlite/embedding 依赖。
