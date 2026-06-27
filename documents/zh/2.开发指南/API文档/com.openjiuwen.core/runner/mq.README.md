# mq

`com.openjiuwen.core.runner.mq` 提供本地消息队列抽象、内存实现以及请求/流式消息封装。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AsyncMessageHandler`](mq/AsyncMessageHandler.md) | `AsyncMessageHandler` 定义 `com.openjiuwen.core.runner.mq` 下的扩展契约。 |
| [`InvokeQueueMessage`](mq/InvokeQueueMessage.md) | 用于请求-响应模式的消息类型，额外提供 `CompletableFuture<Object>` 响应句柄。 |
| [`LocalMessageQueue`](mq/LocalMessageQueue.md) | 本地消息队列占位实现，`start()` 与 `stop()` 直接返回 `true`。 |
| [`MessageQueueBase`](mq/MessageQueueBase.md) | 定义主题订阅、取消订阅与消息投递的抽象消息队列契约。 |
| [`MessageQueueInMemory`](mq/MessageQueueInMemory.md) | 基于内存主题路由的消息队列实现，使用后台消费者线程转发消息。 |
| [`QueueMessage`](mq/QueueMessage.md) | 消息队列通用消息载体，封装消息 ID、载荷与错误信息。 |
| [`StreamQueueMessage`](mq/StreamQueueMessage.md) | 用于流式响应模式的消息类型，额外提供 `CompletableFuture<Iterator<Object>>` 响应句柄。 |
| [`SubscriptionBase`](mq/SubscriptionBase.md) | 定义消息处理器绑定与订阅激活状态控制的抽象基类。 |
| [`SubscriptionInMemory`](mq/SubscriptionInMemory.md) | 基于阻塞队列的内存订阅实现，激活后使用虚拟线程持续消费消息。 |
