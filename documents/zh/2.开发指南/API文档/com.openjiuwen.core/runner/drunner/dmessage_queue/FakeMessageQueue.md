# com.openjiuwen.core.runner.drunner.dmessage_queue.FakeMessageQueue

## 类 FakeMessageQueue

```java
public class FakeMessageQueue extends MessageQueueBase
```

`FakeMessageQueue` 基于 `MessageQueueInMemory` 提供内存消息队列实现，用于分布式 Runner 的兼容层。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `delegate` | `MessageQueueInMemory` | `new MessageQueueInMemory()` | 实际执行发布订阅逻辑的内存消息队列实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void start()` | 启动底层内存消息队列。 |
| `public void stop()` | 停止底层内存消息队列。 |
| `public SubscriptionBase subscribe(String topic)` | 在指定 topic 上创建订阅。 |
| `public void unsubscribe(String topic)` | 取消指定 topic 的订阅。 |
| `public void produceMessage(String topic, QueueMessage message)` | 向指定 topic 投递消息。 |
