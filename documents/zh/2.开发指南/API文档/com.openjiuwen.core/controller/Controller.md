# com.openjiuwen.core.controller.Controller

## class Controller

```java
public class Controller
```

`Controller` 是 ControllerAgent 的核心运行组件，负责连接 `TaskManager`、`EventQueue`、`TaskScheduler` 与 `EventHandler`，并在会话维度上驱动任务创建、调度、流式输出和状态持久化。

## 依赖与状态

| 成员 | 类型 | 说明 |
|---|---|---|
| `card` | `BaseCard` | 当前 Agent 的卡片信息，用于生成 `agentId` 等运行时标识。 |
| `abilityManager` | `Object` | 透传给事件处理器和任务调度链路的能力管理器。 |
| `config` | `ControllerConfig` | 控制器配置；更新后会同步到子模块。 |
| `contextEngine` | `ContextEngine` | 会话上下文引擎，供意图识别、任务调度和状态恢复使用。 |
| `taskManager` | `TaskManager` | 维护任务索引、任务树与 `TaskManagerState`。 |
| `eventQueue` | `EventQueue` | 发布和订阅控制器事件。 |
| `taskScheduler` | `TaskScheduler` | 周期拉取可执行任务并分派执行器。 |
| `eventHandler` | `EventHandler` | 处理输入事件和任务结果事件。 |
| `started` | `boolean` | 标记事件队列与任务调度器是否已经启动。 |

## 构造方法

| 构造方法 | 说明 |
|---|---|
| `Controller()` | 创建空控制器实例；后续需通过 `init(...)` 完成依赖注入与子模块初始化。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `init(BaseCard card, ControllerConfig config, Object abilityManager, ContextEngine contextEngine)` | `void` | 注入核心依赖，并创建 `TaskManager`、`EventQueue` 与 `TaskScheduler`。 |
| `getEventQueue()` | `EventQueue` | 返回当前事件队列实例。 |
| `getConfig()` | `ControllerConfig` | 返回当前控制器配置。 |
| `setConfig(ControllerConfig config)` | `void` | 更新控制器配置，并同步到 `TaskManager`、`EventQueue`、`TaskScheduler` 和 `EventHandler`。 |
| `getContextEngine()` | `ContextEngine` | 返回当前上下文引擎。 |
| `setContextEngine(ContextEngine contextEngine)` | `void` | 更新上下文引擎引用。 |
| `getAbilityManager()` | `Object` | 返回当前能力管理器。 |
| `setAbilityManager(Object abilityManager)` | `void` | 更新能力管理器引用。 |
| `getTaskManager()` | `TaskManager` | 返回任务管理器。 |
| `getTaskScheduler()` | `TaskScheduler` | 返回任务调度器。 |
| `getEventHandler()` | `EventHandler` | 返回当前事件处理器。 |
| `setEventHandler(EventHandler eventHandler)` | `void` | 绑定事件处理器，并注入配置、上下文、调度器、任务管理器与能力管理器。 |
| `addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder)` | `Controller` | 为指定任务类型注册执行器构造器，并返回当前控制器以支持链式调用。 |
| `removeTaskExecutor(String taskType)` | `void` | 从 `TaskExecutorRegistry` 中移除任务类型对应的执行器构造器。 |
| `getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | 通过调度器内部的 `TaskExecutorRegistry` 获取执行器实例。 |
| `start()` | `void` | 启动事件队列、绑定事件处理器并启动任务调度器。 |
| `stop()` | `void` | 停止任务调度器与事件队列，并把 `started` 置为 `false`。 |
| `invoke(InputEvent inputs, AgentSessionApi session)` | `ControllerOutput` | 以批处理模式执行控制器逻辑，聚合流式结果并过滤 `ALL_TASKS_PROCESSED` 信号。 |
| `stream(InputEvent inputs, AgentSessionApi session, List<StreamMode> streamModes)` | `Iterator<Object>` | 自动启动控制器、恢复会话状态、发布输入事件并返回会话流迭代器。 |

## 运行说明

- `stream(...)` 开始时会尝试从会话状态中的 `controller.task_manager_state` 恢复 `TaskManagerState`；缺失或恢复失败时会清空任务管理器状态。
- `setEventHandler(...)` 不会创建新的处理器实例，而是把当前控制器中的配置和依赖绑定到传入实例上。
- 当当前会话没有 `SUBMITTED` 或 `WORKING` 任务时，控制器会立即写出 `ControllerOutputPayload.allTasksProcessed(...)` 对应的完成块。
- 流式迭代在检测到完成块或自然结束后，会统一执行状态保存、取消订阅和会话清理。
