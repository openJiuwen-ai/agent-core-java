# openjiuwen.agent_teams.memory.manager_params

对应 Python 文件：`openjiuwen/agent_teams/memory/manager_params.py`

`TeamMemoryManagerParams` 汇总构造 `TeamMemoryManager` 所需的身份、workspace、运行时句柄、prompt 偏好和团队记忆提取配置。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.TeamMemoryManagerParams`
- `com.openjiuwen.agent_teams.memory.TeamRole`
- `com.openjiuwen.agent_teams.memory.TeamLifecycle`
- `com.openjiuwen.agent_teams.memory.TeamScenario`
- `com.openjiuwen.agent_teams.memory.TeamLanguage`
- `com.openjiuwen.agent_teams.memory.PromptMode`

Literal 对应关系：

- `TeamRole`: `leader`, `teammate`
- `TeamLifecycle`: `temporary`, `persistent`
- `TeamScenario`: `general`, `coding`
- `TeamLanguage`: `cn`, `en`
- `PromptMode`: `proactive`, `passive`

主要字段：

- `memberName` / `teamName`：成员和团队标识。
- `role` / `lifecycle` / `scenario`：团队记忆运行条件。
- `embeddingConfig` / `workspace` / `sysOperation`：成员记忆 toolkit 初始化所需运行时依赖。
- `teamMemoryDir`：团队共享记忆目录。
- `language` / `promptMode`：prompt section 语言和主动/被动模式。
- `enableAutoExtract`：轮次结束时是否允许自动提取团队记忆。
- `readOnlySourceWorkspace`：只读来源 workspace；设置后 manager 会使用该路径构造 workspace。
- `database` / `taskManager` / `extractionModel`：团队记忆提取边界所需运行时句柄。
- `timezoneOffsetHours`：时区偏移，默认 `8.0`。

实现说明：

- Java 枚举使用 `@JsonValue` / `@JsonCreator` 保留 Python Literal 字符串值。
- `TeamMemoryManagerParams` 实现 `TeamMemoryManager.Parameters`，因此可以直接传入 `new TeamMemoryManager(params)`。
- Python dataclass 中的运行时对象字段在 Java 中标为 `@JsonIgnore`，避免把 workspace、数据库、任务管理器、模型等非配置对象序列化。
