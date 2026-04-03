# com.openjiuwen.core.controller.modules.TaskExecutor

## abstract class TaskExecutor

```java
public abstract class TaskExecutor
```

`TaskExecutor` 是任务执行器抽象基类。不同 `taskType` 的业务逻辑需要继承它，并实现任务执行、暂停和取消能力。

## 注入依赖

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 控制器配置。 |
| `abilityManager` | `Object` | 业务能力管理器。 |
| `contextEngine` | `ContextEngine` | 上下文引擎。 |
| `taskManager` | `TaskManager` | 任务管理器。 |
| `eventQueue` | `EventQueue` | 事件发布入口。 |

## 构造方法

### `public TaskExecutor(TaskExecutorDependencies dependencies)`

从 `TaskExecutorDependencies` 中解包所有运行时依赖。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `executeAbility(String taskId, AgentSessionApi session)` | `Iterator<ControllerOutputChunk>` | 执行任务并持续产出输出块。 |
| `canPause(String taskId, AgentSessionApi session)` | `PauseCheckResult` | 判断任务是否允许暂停，并说明原因。 |
| `pause(String taskId, AgentSessionApi session)` | `boolean` | 执行暂停操作。 |
| `canCancel(String taskId, AgentSessionApi session)` | `CancelCheckResult` | 判断任务是否允许取消，并说明原因。 |
| `cancel(String taskId, AgentSessionApi session)` | `boolean` | 执行取消操作。 |

## 嵌套类型

### `public record PauseCheckResult(boolean canPause, String reason)`

封装暂停前检查的结果和失败原因。

### `public record CancelCheckResult(boolean canCancel, String reason)`

封装取消前检查的结果和失败原因。

## 说明

- `TaskScheduler` 会在任务真正启动后把具体执行器实例记录进 `runningTasks`，之后暂停和取消流程会复用同一个执行器实例。
- 执行器产出的 `ControllerOutputChunk` 决定任务最终状态；`TASK_COMPLETION`、`TASK_INTERACTION` 和 `TASK_FAILED` 三类 payload 会驱动调度器更新 `TaskStatus` 并发布对应事件。
