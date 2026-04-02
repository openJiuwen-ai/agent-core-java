# state

`com.openjiuwen.core.session.state` 定义了状态读取、更新、快照恢复、提交回滚以及工作流节点级状态分区的核心抽象。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AgentStateCollection`](./state/AgentStateCollection.md) | 管理 agent 级状态与全局状态分区。 |
| [`CommitStateLike`](./state/CommitStateLike.md) | 为状态对象补充提交、回滚与待提交更新访问能力。 |
| [`InMemoryCommitState`](./state/InMemoryCommitState.md) | 带待提交缓冲区的内存状态实现。 |
| [`InMemoryState`](./state/InMemoryState.md) | 创建内存版 `WorkflowCommitState` 的工厂。 |
| [`InMemoryStateLike`](./state/InMemoryStateLike.md) | `StateLike` 的内存实现。 |
| [`ReadableState`](./state/ReadableState.md) | 只读状态接口。 |
| [`RecoverableState`](./state/RecoverableState.md) | 支持快照导出与恢复的状态接口。 |
| [`State`](./state/State.md) | session 状态管理的顶层接口。 |
| [`StateLike`](./state/StateLike.md) | 可读写的状态接口。 |
| [`WorkflowCommitState`](./state/WorkflowCommitState.md) | 带提交/回滚能力的工作流状态集合。 |
| [`WorkflowStateCollection`](./state/WorkflowStateCollection.md) | 管理 `io/global/comp/workflow` 多分区状态的工作流状态集合。 |

## 说明

- 相关测试：`StateTest`。
