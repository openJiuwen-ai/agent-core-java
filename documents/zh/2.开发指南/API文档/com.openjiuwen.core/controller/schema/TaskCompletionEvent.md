# com.openjiuwen.core.controller.schema.TaskCompletionEvent

## class TaskCompletionEvent

```java
public class TaskCompletionEvent extends Event
```

`TaskCompletionEvent` 表示任务完成事件，承载任务结果数据帧和关联任务对象�?

## 字段

| 字段 | 类型 | 默认�?| 说明 |
|---|---|---|---|
| `taskResult` | `List<DataFrame>` | 空列�?| 任务完成后的输出数据�?|
| `task` | `Task` | `null` | 关联任务对象�?|

## 构造方�?

| 签名 | 说明 |
|---|---|
| `TaskCompletionEvent()` | 创建空完成事件，并固�?`eventType = TASK_COMPLETION`�?|
| `TaskCompletionEvent(List<DataFrame> taskResult, Task task)` | 使用给定结果和任务对象构造完成事件�?|

## 说明

- `TaskScheduler.publishTaskEvent()` 在收�?`task_completion` payload 时会构造该事件并重新送回 `EventQueue`�?
- `EventHandlerWithIntentRecognition.handleTaskCompletion()` 会把 `taskResult` �?`result` 键写入会话流�?
