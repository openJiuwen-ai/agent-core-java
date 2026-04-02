# com.openjiuwen.core.application.workflow.WorkflowIntent

## record WorkflowIntent

```java
public record WorkflowIntent(
    Type intentType,
    Task task,
    WorkflowSchema workflow,
    Map<String, Object> metadata
)
```

`WorkflowIntent` 表示一次工作流意图识别的结果，供 `WorkflowController` 和 `WorkflowEventHandler` 在“启动新任务 / 恢复任务 / 返回默认响应”之间做分派。

## 组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `intentType` | `WorkflowIntent.Type` | 意图类别。 |
| `task` | `Task` | 对应的任务对象；默认响应场景下可为空。 |
| `workflow` | `WorkflowSchema` | 命中的工作流定义；默认响应场景下可为空。 |
| `metadata` | `Map<String, Object>` | 附加元数据；构造时会被复制到新的 `LinkedHashMap`。 |

## 构造行为

### `public WorkflowIntent { ... }`

紧凑构造器会把 `metadata == null` 归一化为 `Map.of()`，否则复制为新的 `LinkedHashMap`，避免调用方继续修改原始映射。

## nested enum Type

| 枚举值 | 说明 |
|---|---|
| `EXEC_NEW_TASK` | 启动一个新的工作流任务。 |
| `RESUME_TASK` | 恢复一个已中断的工作流任务。 |
| `DEFAULT_RESPONSE` | 不执行工作流，直接返回默认响应。 |
