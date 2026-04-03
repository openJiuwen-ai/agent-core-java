# com.openjiuwen.core.controller.schema.TaskInteractionEvent

## class TaskInteractionEvent

```java
public class TaskInteractionEvent extends Event
```

`TaskInteractionEvent` 表示任务执行过程中需要用户交互的事件。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `interaction` | `List<DataFrame>` | 空列表 | 交互内容。 |
| `task` | `Task` | `null` | 关联任务对象。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `TaskInteractionEvent()` | 创建空交互事件，并固定 `eventType = TASK_INTERACTION`。 |
| `TaskInteractionEvent(List<DataFrame> interaction, Task task)` | 使用给定交互内容和任务对象构造事件。 |

## 说明

- `TaskScheduler` 在执行器输出 `task_interaction` payload 时，会把 payload 数据封装为该事件并通过 `EventQueue` 重新分发。
- `EventHandlerWithIntentRecognition.handleTaskInteraction()` 会把 `interaction` 直接写入会话流。
