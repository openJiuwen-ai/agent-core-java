# resourcemanager

`com.openjiuwen.core.runner.resourcemanager` 统一管理 Agent、Workflow、Tool、Prompt、Model、MCP 与系统操作资源。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AbstractManager`](resourcemanager/AbstractManager.md) | 基于 provider 注册机制的通用资源管理器基类。 |
| [`AgentGroupMgr`](resourcemanager/AgentGroupMgr.md) | 负责 `AgentGroup` 资源 provider 的注册、获取与移除。 |
| [`AgentMgr`](resourcemanager/AgentMgr.md) | 负责 `Agent` 资源 provider 的注册、获取与移除。 |
| [`ModelMgr`](resourcemanager/ModelMgr.md) | 负责 `Model` 资源 provider 的注册、获取与移除。 |
| [`PromptMgr`](resourcemanager/PromptMgr.md) | 负责 `PromptTemplate` 的注册、批量导入、查询、移除与清空。 |
| [`ResourceMgr`](resourcemanager/ResourceMgr.md) | `ResourceMgr` 是统一资源门面，负责 Agent、Workflow、Tool、Prompt、Model、MCP Server 与 SysOperation 的注册、查询、移除和标签管理。 |
| [`ResourceRegistry`](resourcemanager/ResourceRegistry.md) | 聚合各类资源子管理器，并提供统一清理和按资源 ID 移除入口。 |
| [`SysOperationMgr`](resourcemanager/SysOperationMgr.md) | 负责 `SysOperation` 实例的注册、获取、移除与清空。 |
| [`TagMgr`](resourcemanager/TagMgr.md) | 负责资源与标签的双向索引、组合匹配和调试展示。 |
| [`ThreadSafeDict`](resourcemanager/ThreadSafeDict.md) | 基于 `ReentrantLock` 和 `HashMap` 的线程安全字典封装。 |
| [`ToolMgr`](resourcemanager/ToolMgr.md) | 负责普通 `Tool`、MCP Server 工具以及 `SysOperation` 关联工具的注册与释放。 |
| [`WorkflowMgr`](resourcemanager/WorkflowMgr.md) | 负责 `Workflow` 资源 provider 的注册、批量导入、获取与移除。 |
