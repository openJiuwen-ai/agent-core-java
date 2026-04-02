# com.openjiuwen.core.workflow.WorkflowOutput

## 类 WorkflowOutput

```java
public class WorkflowOutput
```

`WorkflowOutput` 封装工作流最终返回值与执行状态。

## 关键字段

| 字段 | 说明 |
| --- | --- |
| `result` | 工作流输出结果。 |
| `state` | 对应的 `WorkflowExecutionState`。 |

## 方法

- 提供无参构造和 `(Object result, WorkflowExecutionState state)` 全参构造。
- 通过 getter/setter 读写 `result` 与 `state`。
- `toString()` 返回调试友好的字符串表示。
