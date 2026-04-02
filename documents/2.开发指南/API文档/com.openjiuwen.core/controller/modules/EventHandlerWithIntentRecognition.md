# com.openjiuwen.core.controller.modules.EventHandlerWithIntentRecognition

## class EventHandlerWithIntentRecognition

```java
public class EventHandlerWithIntentRecognition extends EventHandler
```

`EventHandlerWithIntentRecognition` 是控制器默认的事件处理器实现。它在收到 `InputEvent` 后先调用 `IntentRecognizer` 识别意图，再按意图类型创建、暂停、恢复、补充、取消或修改任务；对任务交互、完成和失败事件则直接写回会话流。

## 构造方法

### `public EventHandlerWithIntentRecognition(IntentRecognizer.ModelProvider modelProvider)`

创建处理器，并保存获取模型实例所需的 `ModelProvider`。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `initRecognizer()` | `void` | 在依赖已注入后创建 `IntentRecognizer`。 |
| `handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | 识别一个或多个意图，并为每个意图启动虚拟线程执行对应的任务操作。 |
| `handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | 仅接受 `TaskInteractionEvent`，并把 `interaction` 写入会话流。 |
| `handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | 仅接受 `TaskCompletionEvent`，并把 `result` 写入会话流。 |
| `handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | 仅接受 `TaskFailedEvent`，并把 `error_message` 写入会话流。 |

## 意图路由

| 意图类型 | 处理方式 |
|---|---|
| `CREATE_TASK` | 新建 `Task`，默认 `taskType = "default_task_type"`，状态设为 `SUBMITTED`。 |
| `PAUSE_TASK` | 调用 `TaskScheduler.pauseTask(targetTaskId)`。 |
| `RESUME_TASK` | 查找目标任务，仅当其状态为 `PAUSED` 时改回 `SUBMITTED`。 |
| `CONTINUE_TASK` | 合并依赖任务的输入事件和相关 `ModelContext` 消息，再创建新的 `SUBMITTED` 任务。 |
| `SUPPLEMENT_TASK` | 暂停目标任务，把补充信息追加到描述后重新提交。 |
| `CANCEL_TASK` | 调用 `TaskScheduler.cancelTask(targetTaskId)`。 |
| `MODIFY_TASK` | 先取消目标任务，再更新描述、追加当前输入事件并重新提交。 |
| `UNKNOWN_TASK` | 向会话流写入 `clarification_prompt`。 |

## 说明

- `handleInput()` 会为每个识别出的意图创建一个虚拟线程，并在返回前等待这些线程全部结束，因此一个输入事件可以一次驱动多个任务操作。
- 单元测试覆盖了创建、暂停、恢复、取消、补充、修改和未知意图分支，以及交互/完成/失败事件的类型检查。
- 当前实现要求 `MODIFY_TASK` 和 `SUPPLEMENT_TASK` 的 `targetTaskId` 指向已存在任务；如果调用方构造的 `Intent` 不满足这个约束，更新不会生效。
