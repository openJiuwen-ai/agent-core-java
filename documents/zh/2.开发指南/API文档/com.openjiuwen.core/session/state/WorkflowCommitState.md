# com.openjiuwen.core.session.state.WorkflowCommitState

## 类 WorkflowCommitState

```java
public class WorkflowCommitState extends WorkflowStateCollection
```

在 `WorkflowStateCollection` 之上增加提交、回滚、快照与节点状态创建能力的工作流状态实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowCommitState(CommitStateLike ioState, CommitStateLike globalState, CommitStateLike compState, CommitStateLike workflowState, Map<String, Object> traceState, String parentId, String nodeId)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void commit()` | 提交全部状态分区，并把每个分区中所有节点的待提交更新一次性落盘。 |
| `public void commitCmp()` | 仅提交当前节点的组件状态与 IO 状态。 |
| `public void commitWorkflow()` | 仅提交当前节点的工作流状态分区。 |
| `public void updateAndCommitWorkflowState(Map<String, Object> data)` | 更新工作流状态后立即提交。 |
| `public void rollback()` | 回滚当前节点在各状态分区中的待提交更新。 |
| `public Map<String, Object> getState()` | 导出包含 `io/global/comp/workflow/trace` 的完整状态快照。 |
| `public void setState(Map<String, Object> state)` | 从完整状态快照恢复各状态分区。 |
| `public WorkflowCommitState createNodeState(String newNodeId, String newParentId)` | 基于共享分区创建新的节点级状态对象。 |
| `public WorkflowCommitState createNodeState(String newNodeId)` | 只指定节点 ID 的兼容重载。 |
| `public CommitStateLike getIoState()` | 返回 IO 状态分区。 |
| `public CommitStateLike getGlobalState()` | 返回全局状态分区。 |
| `public CommitStateLike getCompState()` | 返回组件状态分区。 |
| `public CommitStateLike getWorkflowState()` | 返回工作流状态分区。 |
| `public Map<String, Object> getTraceState()` | 返回 trace 状态分区。 |
| `public Map<String, Object> getUpdates()` | 返回所有分区的待提交更新快照。 |
| `public void setUpdates(Map<String, Object> updates)` | 从快照恢复所有分区的待提交更新。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`StateTest`、`WorkflowInteractionTest`。
