# com.openjiuwen.core.controller.schema.TaskFailedEvent

## class TaskFailedEvent

```java
public class TaskFailedEvent extends Event
```

`TaskFailedEvent` 表示任务失败事件，承载错误消息和关联任务对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `errorMessage` | `String` | `null` | 失败原因。 |
| `task` | `Task` | `null` | 关联任务对象。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `TaskFailedEvent()` | 创建空失败事件，并固定 `eventType = TASK_FAILED`。 |
| `TaskFailedEvent(String errorMessage, Task task)` | 使用错误信息和任务对象构造失败事件。 |

## 说明

- `TaskScheduler` 在执行器抛错、超时或返回 `task_failed` payload 时，会构造该事件并写回 `EventQueue`。
- `EventHandlerWithIntentRecognition.handleTaskFailed()` 会把 `errorMessage` 以同名键写入会话流。
