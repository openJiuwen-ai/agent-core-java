# pregel

`com.openjiuwen.core.graph.pregel` 提供 Pregel/BSP 运行时、channel 路由、中断处理、循环执行与任务调度原语。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BarrierChannel`](./pregel/BarrierChannel.md) | 用于 N→1 汇聚同步的 barrier channel。 |
| [`BarrierMessage`](./pregel/BarrierMessage.md) | 用于 N→1 汇聚同步的 barrier 消息。 |
| [`BarrierRouter`](./pregel/BarrierRouter.md) | 向 barrier channel 发送 `BarrierMessage` 的路由器。 |
| [`Channel`](./pregel/Channel.md) | Pregel 节点之间传递消息的抽象 channel。 |
| [`ChannelManager`](./pregel/ChannelManager.md) | 管理所有 channel，并负责消息缓冲、刷新与 ready 节点判定。 |
| [`ConditionalRouter`](./pregel/ConditionalRouter.md) | 通过 selector 动态决定目标节点的条件路由器。 |
| [`GraphInterrupt`](./pregel/GraphInterrupt.md) | 图执行被中断时抛出的异常。 |
| [`IRouter`](./pregel/IRouter.md) | 节点执行完成后分发消息的路由接口。 |
| [`Interrupt`](./pregel/Interrupt.md) | 图执行过程中的中断值封装。 |
| [`Message`](./pregel/Message.md) | 在 Pregel 节点之间经由 channel 传递的基础消息。 |
| [`NodeTask`](./pregel/NodeTask.md) | 执行单个 Pregel 节点并产出路由消息的任务单元。 |
| [`Pregel`](./pregel/Pregel.md) | 实现 BSP 模型的 Pregel 图执行引擎。 |
| [`PregelBuilder`](./pregel/PregelBuilder.md) | 用于构建 `Pregel` 引擎的 builder。 |
| [`PregelConfig`](./pregel/PregelConfig.md) | Pregel 图执行配置对象。 |
| [`PregelConstants`](./pregel/PregelConstants.md) | Pregel 执行引擎使用的常量定义。 |
| [`PregelLoop`](./pregel/PregelLoop.md) | 实现 BSP 超步调度的 Pregel 执行循环。 |
| [`PregelNode`](./pregel/PregelNode.md) | Pregel 执行图中的节点定义。 |
| [`StaticRouter`](./pregel/StaticRouter.md) | 向固定目标发送 `TriggerMessage` 的静态路由器（1→N）。 |
| [`TaskExecutorPool`](./pregel/TaskExecutorPool.md) | 基于虚拟线程并发执行节点任务的任务池。 |
| [`TriggerChannel`](./pregel/TriggerChannel.md) | 收到任意触发消息即变为 ready 的 channel。 |
| [`TriggerMessage`](./pregel/TriggerMessage.md) | 在下一次 super-step 激活目标节点的触发消息。 |

## 说明

- 代表性测试：`ChannelTest`、`CompiledGraphTest`、`GraphStoreTest`、`PregelTest`、`TaskExecutorPoolTest`。
