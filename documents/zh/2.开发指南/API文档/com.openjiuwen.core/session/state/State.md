# com.openjiuwen.core.session.state.State

## 接口 State

```java
public interface State extends RecoverableState
```

session 状态管理的顶层接口，定义全局状态、组件状态、trace 状态与常用分区键。

## 字段

| 签名 | 说明 |
| --- | --- |
| `String GLOBAL_STATE_KEY = "global_state"` | 全局状态分区键。 |
| `String IO_STATE_KEY = "io_state"` | 输入输出状态分区键。 |
| `String IO_STATE_UPDATES_KEY = "io_state_updates"` | 输入输出状态待提交更新键。 |
| `String GLOBAL_STATE_UPDATES_KEY = "global_state_updates"` | 全局状态待提交更新键。 |
| `String COMP_STATE_KEY = "comp_state"` | 组件状态分区键。 |
| `String COMP_STATE_UPDATES_KEY = "comp_state_updates"` | 组件状态待提交更新键。 |
| `String WORKFLOW_STATE_KEY = "workflow_state"` | 工作流状态分区键。 |
| `String WORKFLOW_STATE_UPDATES_KEY = "workflow_state_updates"` | 工作流状态待提交更新键。 |
| `String AGENT_STATE_KEY = "agent_state"` | agent 状态分区键。 |
| `String TRACE_STATE_KEY = "trace_state"` | trace 状态分区键。 |
| `String DEFAULT_NODE_ID = "default"` | 默认节点 ID。 |
| `String DEFAULT_WORKFLOW_ID = "workflow"` | 默认工作流 ID。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `Object getGlobal(Object key)` | 按键读取全局状态。 |
| `void updateGlobal(Map<String, Object> data)` | 更新全局状态。 |
| `void updateTrace(Object span)` | 更新 trace 状态。 |
| `void update(Map<String, Object> data)` | 更新组件/局部状态。 |
| `Object get(Object key)` | 按键读取组件/局部状态。 |
| `Map<String, Object> dump()` | 导出调试用的完整状态视图。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`StateTest`、`StreamOutputFullTest`、`StreamOutputTest`、`WorkflowInteractionTest`。
