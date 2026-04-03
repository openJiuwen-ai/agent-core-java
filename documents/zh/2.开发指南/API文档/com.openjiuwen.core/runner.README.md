# runner

`com.openjiuwen.core.runner` 提供全局运行器入口、运行配置以及分布式运行所需的消息队列配置。

## 子包

| Package | Description |
| --- | --- |
| [`base`](runner/base.README.md) | 定义运行器资源注册和标签系统共用的 Provider、结果和标签基础类型。 |
| [`callback`](runner/callback.README.md) | 提供事件驱动回调框架、过滤器、链式执行和指标统计能力。 |
| [`drunner`](runner/drunner.README.md) | 封装分布式 Runner 的运行时入口以及消息队列启动流程。 |
| [`mq`](runner/mq.README.md) | 提供本地消息队列抽象、内存实现以及请求/流式消息封装。 |
| [`resourcemanager`](runner/resourcemanager.README.md) | 统一管理 Agent、Workflow、Tool、Prompt、Model、MCP 与系统操作资源。 |

## 类型

| Type | Description |
| --- | --- |
| [`DistributedConfig`](runner/DistributedConfig.md) | `DistributedConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。 |
| [`MessageQueueConfig`](runner/MessageQueueConfig.md) | `MessageQueueConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。 |
| [`MessageQueueType`](runner/MessageQueueType.md) | Message queue type enumeration. |
| [`PulsarConfig`](runner/PulsarConfig.md) | `PulsarConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。 |
| [`Runner`](runner/Runner.md) | `Runner` 是全局单例门面，统一代理 `RunnerImpl` 的生命周期、资源管理器访问以及 workflow / agent / agent group 执行入口。 |
| [`RunnerConfig`](runner/RunnerConfig.md) | `RunnerConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。 |
| [`RunnerImpl`](runner/RunnerImpl.md) | `RunnerImpl` 负责装配资源管理器、本地消息队列、回调框架和分布式运行支持，并提供 workflow / agent / group 的实际执行逻辑。 |
