# com.openjiuwen.core.session.state.InMemoryState

## 类 InMemoryState

```java
public final class InMemoryState
```

创建内存版 `WorkflowCommitState` 的工厂类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static WorkflowCommitState create(Map<String, Object> ioState, Map<String, Object> globalState, Map<String, Object> compState, Map<String, Object> workflowState, Map<String, Object> traceState)` | 使用给定分区状态创建一个新的内存版 `WorkflowCommitState`。 |
| `public static WorkflowCommitState create()` | 创建所有分区均为空的默认工作流状态。 |
| `public static WorkflowCommitState fromMap(Map<String, Object> stateMap)` | 从完整状态快照恢复一个 `WorkflowCommitState`。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`StateTest`、`WorkflowInteractionTest`。
