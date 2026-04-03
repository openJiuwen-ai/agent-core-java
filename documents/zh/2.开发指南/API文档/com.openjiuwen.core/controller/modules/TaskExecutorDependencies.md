# com.openjiuwen.core.controller.modules.TaskExecutorDependencies

## class TaskExecutorDependencies

```java
public class TaskExecutorDependencies
```

`TaskExecutorDependencies` 把构造 `TaskExecutor` 需要的控制器依赖打包为单个对象，避免执行器注册时传递过长的参数列表。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 控制器配置。 |
| `abilityManager` | `Object` | 业务能力管理器。 |
| `contextEngine` | `ContextEngine` | 上下文引擎。 |
| `taskManager` | `TaskManager` | 任务管理器。 |
| `eventQueue` | `EventQueue` | 事件分发器。 |

## 构造方法

### `public TaskExecutorDependencies(ControllerConfig config, Object abilityManager, ContextEngine contextEngine, TaskManager taskManager, EventQueue eventQueue)`

一次性注入执行器构造所需的全部依赖。

## 说明

- `TaskScheduler.executeTask()` 会在实例化执行器前构造该对象。
- 该类型只有 getter，没有 setter；依赖关系在构造后保持稳定。
