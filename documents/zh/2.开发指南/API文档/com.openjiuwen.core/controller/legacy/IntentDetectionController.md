# com.openjiuwen.core.controller.legacy.IntentDetectionController

## abstract class IntentDetectionController

```java
public abstract class IntentDetectionController extends BaseController
```

`IntentDetectionController` 是旧版带意图检测的控制器基类，支持“新请求到达时优先打断旧请求”的实时中断语义。

## 核心状态

| 成员 | 类型 | 说明 |
|---|---|---|
| `taskQueue` | `TaskQueue` | 记录会话级别正在运行的任务。 |
| `processingHandlers` | `Map<String, Thread>` | 跟踪每个 `conversationId` 当前正在处理输入的线程。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `invoke(Map<String, Object> inputs, Session session)` | `Map<String, Object>` | 新请求到达时先打断旧 handler，再取消 `TaskQueue` 中的运行任务，最后委托给父类。 |

## 嵌套类型

| 类型 | 说明 |
|---|---|
| `IntentType` | 意图分类枚举，区分新建任务、恢复任务、取消任务、默认回复和未知意图。 |
| `Intent` | Lombok 数据类，保存识别后的意图类型、目标任务、关联 workflow 与元数据。 |
| `TaskQueue` | 维护会话级运行任务索引，并提供注册、查找、取消与注销操作。 |
| `RunningTaskInfo` | 保存运行中的任务对象、异步句柄、目标标识和启动时间。 |

## 扩展钩子

- `handleEvent(Event event, Session session)` 会在注册当前处理线程后调用 `intentDetection()`，再按意图结果分发到各处理分支。
- `handleNewTask(...)` 会先把任务状态置为 `PENDING`，再委托 `execTask(...)` 执行。
- `handleResume(...)` 会从 session 状态推断目标组件，并用新的用户输入重建 `InteractiveInput`。
- `handleCancel(...)` 会把任务状态置为 `CANCELLED` 并返回取消结果。
- `handleDefaultResponse(...)` 会向 `AgentSessionApi` 写入默认回复流。
- `handleUnknownIntent(...)` 用于输出统一的未知意图错误结果。
- `intentDetection(Event event, Session session)`、`execTask(...)` 和 `interruptTask(...)` 都是受保护的抽象扩展点，由具体子类实现。

## 说明

- 如果新的请求打断了当前 handler，`handleEvent()` 会返回 `{"status": "cancelled"}` 而不是继续抛错。
- `handleResume()` 会尝试从 `workflow_controller.interrupted_tasks` 中找出被打断组件 ID，并将新的用户输入映射到对应组件。
