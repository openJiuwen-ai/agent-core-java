# modules

`com.openjiuwen.core.controller.modules` 提供控制器运行时的基础设施类型，包括事件处理、意图识别、任务执行器注册、任务管理和任务调度。

## Types

| 类型 | 说明 |
|---|---|
| [`EventHandler`](./modules/EventHandler.md) | 事件处理器抽象基类，定义输入、交互、完成和失败四类事件入口。 |
| [`EventHandlerInput`](./modules/EventHandlerInput.md) | 传给事件处理器的入参封装，包含事件对象和会话对象。 |
| [`EventHandlerWithIntentRecognition`](./modules/EventHandlerWithIntentRecognition.md) | 内置意图识别的默认事件处理器，实现创建、暂停、恢复、补充、取消和修改任务。 |
| [`EventQueue`](./modules/EventQueue.md) | 基于 topic 的同步事件队列与订阅分发器。 |
| [`IntentRecognizer`](./modules/IntentRecognizer.md) | 基于 LLM tool calling 的意图识别器。 |
| [`IntentToolkits`](./modules/IntentToolkits.md) | 为意图识别器生成 OpenAI 工具 schema，并把 tool call 结果转成 `Intent`。 |
| [`TaskExecutor`](./modules/TaskExecutor.md) | 任务执行器抽象基类，定义执行、暂停和取消接口。 |
| [`TaskExecutorDependencies`](./modules/TaskExecutorDependencies.md) | 构造 `TaskExecutor` 时注入的依赖集合。 |
| [`TaskExecutorRegistry`](./modules/TaskExecutorRegistry.md) | 按 `taskType` 注册和实例化任务执行器。 |
| [`TaskFilter`](./modules/TaskFilter.md) | 查询任务时使用的过滤条件对象。 |
| [`TaskManager`](./modules/TaskManager.md) | 任务存储、索引、父子关系和状态更新的核心管理器。 |
| [`TaskManagerState`](./modules/TaskManagerState.md) | `TaskManager` 的可序列化状态对象。 |
| [`TaskScheduler`](./modules/TaskScheduler.md) | 周期扫描 `SUBMITTED` 任务并使用虚拟线程执行的调度器。 |

## Notes

- 该子包同时覆盖事件流和任务流两条主线：`EventQueue` 负责输入/结果事件，`TaskManager` 与 `TaskScheduler` 负责任务生命周期。
- `IntentRecognizer` 与 `IntentToolkits` 组合后，为 `EventHandlerWithIntentRecognition` 提供基于工具调用的意图路由能力。
