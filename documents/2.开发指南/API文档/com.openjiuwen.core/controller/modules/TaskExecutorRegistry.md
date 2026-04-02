# com.openjiuwen.core.controller.modules.TaskExecutorRegistry

## class TaskExecutorRegistry

```java
public class TaskExecutorRegistry
```

`TaskExecutorRegistry` 维护 `taskType -> builder` 的映射，用于在调度时按任务类型创建对应的 `TaskExecutor`。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder)` | `void` | 注册或覆盖某个 `taskType` 的执行器构造器。 |
| `removeTaskExecutor(String taskType)` | `void` | 移除某个 `taskType` 的执行器构造器；不存在时直接忽略。 |
| `getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | 根据 `taskType` 创建新的执行器实例；未注册时抛出 `AGENT_CONTROLLER_TASK_EXECUTION_ERROR`。 |

## 说明

- 单元测试确认：同一 `taskType` 重复注册时，后注册的 builder 会覆盖前一个。
- `getTaskExecutor()` 每次都会重新调用 builder，因此每次取回的执行器都是新实例，而不是缓存对象。
- 调度器和 `Controller.addTaskExecutor()` 共享同一个注册表实例。
