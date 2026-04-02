# com.openjiuwen.core.workflow.WorkflowExecutionState

## 枚举 WorkflowExecutionState

```java
public enum WorkflowExecutionState
```

`WorkflowExecutionState` 表示工作流执行后的状态。

## 枚举值

| 值 | 说明 |
| --- | --- |
| `COMPLETED` | 正常完成。 |
| `INPUT_REQUIRED` | 需要额外交互输入。 |
| `ERROR` | 执行失败。 |

## 说明

- `WorkflowTest` 的普通成功场景显式断言结果状态为 `COMPLETED`。
