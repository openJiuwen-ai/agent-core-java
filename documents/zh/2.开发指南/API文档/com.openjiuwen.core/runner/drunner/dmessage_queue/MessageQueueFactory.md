# com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory

## 类 MessageQueueFactory

```java
public final class MessageQueueFactory
```

`MessageQueueFactory` 根据 `MessageQueueConfig` 选择分布式 Runner 使用的消息队列实现。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static MessageQueueBase create(MessageQueueConfig config)` | 根据配置选择消息队列实现；当前即使配置为 `pulsar` 也只记录告警并回退到 `FakeMessageQueue`。 |
