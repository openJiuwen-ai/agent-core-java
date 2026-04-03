# com.openjiuwen.core.controller.modules.TaskScheduler

## class TaskScheduler

```java
public class TaskScheduler
```

`TaskScheduler` 负责周期扫描 `SUBMITTED` 任务，并为每个任务创建 `TaskExecutor`、驱动执行输出、更新任务状态，以及处理暂停、取消和完成信号。

## 核心状态

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 调度和超时配置。 |
| `taskManager` | `TaskManager` | 任务存储与状态更新入口。 |
| `contextEngine` | `ContextEngine` | 为执行器提供上下文能力。 |
| `abilityManager` | `Object` | 为执行器提供业务能力。 |
| `eventQueue` | `EventQueue` | 发布任务完成、交互和失败事件。 |
| `taskExecutorRegistry` | `TaskExecutorRegistry` | `taskType` 到执行器构造器的注册表。 |
| `sessions` | `Map<String, AgentSessionApi>` | 当前活跃会话。 |
| `runningTasks` | `Map<String, RunningTaskEntry>` | 当前运行中的任务线程和执行器实例。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getTaskExecutorRegistry()` | `TaskExecutorRegistry` | 获取执行器注册表。 |
| `pauseTask(String taskId)` | `boolean` | 在会话和执行器都存在时，先调用 `canPause()/pause()`，再中断虚拟线程并把状态改为 `PAUSED`。 |
| `cancelTask(String taskId)` | `boolean` | 在会话和执行器都存在时，先调用 `canCancel()/cancel()`，再中断虚拟线程并把状态改为 `CANCELED`。 |
| `start()` | `void` | 启动定时调度循环，按 `scheduleInterval` 扫描 `SUBMITTED` 任务。 |
| `stop()` | `void` | 停止调度线程、打断全部运行中任务，并等待线程收尾。 |

## 执行流程

1. `scheduleLoop()` 定期读取所有 `SUBMITTED` 任务。
2. 为每个待执行任务查找会话、检查并发上限，并在虚拟线程中调用 `executeTaskWrapper()`。
3. `executeTaskWrapper()` 负责超时看门狗、异常包装和完成信号检查。
4. `executeTask()` 取出 `Task`、构造 `TaskExecutorDependencies`、实例化执行器、把任务状态更新为 `WORKING`，然后持续消费 `ControllerOutputChunk`。
5. 当输出 payload 类型为 `task_completion`、`task_interaction` 或 `task_failed` 时，调度器会更新 `TaskStatus`、发布对应事件，并在必要时向会话写入 `all_tasks_processed` 结束块。

## 说明

- 若 `ControllerConfig.taskTimeout` 非空，调度器会为任务启动一个 watchdog 线程，在超时后中断执行线程并把任务标记为失败。
- `publishTaskEvent()` 会把输出 payload 转换成 `TaskCompletionEvent`、`TaskInteractionEvent` 或 `TaskFailedEvent` 再回送到 `EventQueue`。
- `areAllTasksCompleted()` 只把 `SUBMITTED` 和 `WORKING` 视为活跃状态，因此暂停、取消、失败和输入等待都允许会话收到完成信号。
- `runningTasks` 中保存的 `RunningTaskEntry` 会在任务结束、暂停或取消后被移除，避免旧执行器被后续任务误用。
