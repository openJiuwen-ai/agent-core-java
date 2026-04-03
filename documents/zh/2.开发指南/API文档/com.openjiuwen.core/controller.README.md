# controller

`com.openjiuwen.core.controller` 提供 ControllerAgent 的控制器主入口，以及围绕事件分发、任务调度、意图识别和控制器输入输出模型的一组基础类型。

## Modules

| 模块 | 说明 |
|---|---|
| [`legacy`](./controller/legacy.README.md) | 旧版控制器兼容层，保留早期事件模型、reasoner 和任务结构。 |
| [`modules`](./controller/modules.README.md) | 事件处理器、事件队列、任务执行器、任务管理器、调度器与意图识别基础设施。 |
| [`schema`](./controller/schema.README.md) | 控制器输入事件、任务与意图模型、输出 chunk/payload 与状态枚举。 |

## Types

| 类型 | 说明 |
|---|---|
| [`Controller`](./controller/Controller.md) | 控制器主入口，协调 `EventQueue`、`TaskScheduler`、`TaskManager` 与 `EventHandler`。 |
| [`ControllerConfig`](./controller/ControllerConfig.md) | 控制器配置对象，定义并发、超时、事件队列与意图识别参数。 |

## Notes

- 本页同时链接新的 `modules` / `schema` 子包，以及兼容旧版调用路径的 `legacy` 子包。
- 新业务优先参考 `modules` 与 `schema`；只有兼容旧版控制器时再进入 `legacy`。
