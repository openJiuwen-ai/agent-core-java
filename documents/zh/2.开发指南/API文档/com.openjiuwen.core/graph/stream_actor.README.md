# stream_actor

`com.openjiuwen.core.graph.stream_actor` 提供图节点之间的流式消息协调能力，包括 actor 生命周期、payload 协议、consumer 注册、schema 驱动的迭代器生成和输入转换工具。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ActorManager`](./stream_actor/ActorManager.md) | 根据 producer/consumer 边与组件能力构建 `StreamActor`，并统一负责生产、消费与关闭。 |
| [`StreamActor`](./stream_actor/StreamActor.md) | 管理单个消费节点的流式调用生命周期，为 `COLLECT` / `TRANSFORM` 能力维护处理器。 |
| [`StreamConsumer`](./stream_actor/StreamConsumer.md) | 由图节点实现的流式消费接口，定义启动、可处理判断与完成状态。 |
| [`StreamGraph`](./stream_actor/StreamGraph.md) | 保存节点 ID 到 `StreamConsumer` 的注册关系。 |
| [`StreamPayload`](./stream_actor/StreamPayload.md) | 在 producer 与 consumer 之间传递的消息载体，包含原始消息和来源能力。 |
| [`StreamProcessor`](./stream_actor/StreamProcessor.md) | 将消息路由到 schema 叶子路径对应的阻塞迭代器，并负责 END 帧收敛。 |
| [`StreamTransform`](./stream_actor/StreamTransform.md) | 提供自定义 transformer 和 schema 两种输入转换方式。 |

## 说明

- `ActorManager` 会把 `streamEdges` 反向展开为 consumer 到 producer 的关系，并仅为具备 `COLLECT` / `TRANSFORM` 能力的节点创建 `StreamActor`。
- `StreamProcessorTest` 覆盖了 schema 叶子路由、回调触发与结束帧处理；`ActorManager`、`StreamActor`、`StreamTransform` 的说明来自源码人工核对。
