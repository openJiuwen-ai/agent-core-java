# com.openjiuwen.core.session.state.WorkflowStateCollection

## 类 WorkflowStateCollection

```java
public class WorkflowStateCollection implements State
```

管理 `io/global/comp/workflow` 多个状态分区的工作流状态集合实现。

## 字段

| 签名 | 说明 |
| --- | --- |
| `protected final CommitStateLike ioState` | IO 状态分区。 |
| `protected final CommitStateLike globalState` | 全局状态分区。 |
| `protected final CommitStateLike compState` | 组件状态分区。 |
| `protected final CommitStateLike workflowState` | 工作流状态分区。 |
| `protected Map<String, Object> traceState` | trace 状态缓存。 |
| `protected String parentId` | 父节点 ID。 |
| `protected String nodeId` | 当前节点 ID。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowStateCollection(CommitStateLike ioState, CommitStateLike globalState, CommitStateLike compState, CommitStateLike workflowState, Map<String, Object> traceState, String parentId, String nodeId)` | 使用给定分区状态、trace 缓存和节点标识创建工作流状态集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getGlobal(Object key)` | 读取全局状态；如果全局分区缺值，会继续尝试从 `ioState` 的父节点和当前节点前缀下回退读取。 |
| `public void updateGlobal(Map<String, Object> data)` | 把数据记入当前节点对应的全局状态待提交区。 |
| `public void updateTrace(Object span)` | 把当前节点的 trace 数据写入 `traceState`。 |
| `public void update(Map<String, Object> data)` | 把组件状态包装在当前 `nodeId` 下写入组件状态待提交区。 |
| `public Object get(Object key)` | 读取当前节点的组件状态；`key = null` 时返回节点整段状态。 |
| `public Object getWorkflow(Object key)` | 读取工作流级状态。 |
| `public void updateWorkflow(Map<String, Object> data)` | 更新当前节点的工作流级状态。 |
| `public Map<String, Object> dump()` | 导出包含已提交状态与待提交更新的完整调试视图。 |
| `public void commitCmp()` | 提交当前节点的组件状态与 IO 状态。 |
| `public Object getInputs(Object schema)` | 根据 `schema` 从 IO 状态分区解析当前节点输入。 |
| `public Object getInputsByTransformer(Object transformer)` | 使用转换函数基于 `dump()` 结果计算输入。 |
| `public void setOutputs(Object results)` | 把当前节点输出写入 IO 状态分区。 |
| `public Object getOutputs(String outputNodeId)` | 读取指定节点的输出；未指定时默认读取当前节点。 |
| `public void commitUserInputs(Map<String, Object> inputs)` | 写入用户输入到 IO 与全局状态，并立即提交。 |
| `public void commit()` | 提交所有工作流状态分区。 |
| `public WorkflowCommitState createNodeState(String newNodeId, String newParentId)` | 基于共享分区创建新的节点级状态对象。 |
| `public Map<String, Object> getState()` | 默认返回空映射，供子类覆盖。 |
| `public void setState(Map<String, Object> state)` | 默认空实现，供子类覆盖。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`StateTest`、`WorkflowInteractionTest`。
