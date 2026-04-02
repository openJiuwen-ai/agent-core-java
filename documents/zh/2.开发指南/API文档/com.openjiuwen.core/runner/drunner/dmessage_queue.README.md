# dmessage_queue

`com.openjiuwen.core.runner.drunner.dmessage_queue` 提供分布式消息队列工厂、消息序列化器和测试替身实现。

## 子包

| 包 | 说明 |
| --- | --- |
| [`dsubscription`](dmessage_queue/dsubscription.README.md) | 管理 `reply topic` 订阅、取消事件和响应收集器。 |
| [`message`](dmessage_queue/message.README.md) | 定义分布式请求与响应消息模型及其类型枚举。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`FakeMessageQueue`](dmessage_queue/FakeMessageQueue.md) | 基于 `MessageQueueInMemory` 的内存消息队列替身，用于分布式 Runner 兼容层或本地联调。 |
| [`MessageQueueFactory`](dmessage_queue/MessageQueueFactory.md) | 根据配置返回分布式 Runner 使用的消息队列实现，当前统一回退到 `FakeMessageQueue`。 |
| [`MessageSerializer`](dmessage_queue/MessageSerializer.md) | 负责分布式消息及其嵌套负载的 JSON 序列化与反序列化。 |
