# openjiuwen.agent_teams.agent.resources

## PrivateAgentResources

`PrivateAgentResources` 是单个 TeamAgent 实例持有的私有运行时资源容器，对应 Python 源文件 `openjiuwen/agent_teams/agent/resources.py`。

Java 侧将它实现为 `AgentConfigurator.PrivateAgentResources`，因为当前翻译中这些私有资源只由 `AgentConfigurator` 创建和转发。

字段语义与 Python dataclass 保持一致，默认均为空：

- `harness`: 成员运行时入口。
- `worktreeManager`: 单成员 worktree 管理器。
- `memoryManager`: team memory 运行时资源。
- `firstIterGate`: 首轮迭代门禁。
- `modelAllocator`: leader 侧模型分配器。

容器只保存资源引用，不负责构造、关闭或复制资源。
