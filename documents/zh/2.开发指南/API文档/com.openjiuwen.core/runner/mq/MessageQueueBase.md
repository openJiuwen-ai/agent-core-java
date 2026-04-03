# com.openjiuwen.core.runner.mq.MessageQueueBase

## 类 MessageQueueBase

```java
public abstract class MessageQueueBase
```

`MessageQueueBase` 定义主题订阅、取消订阅与消息投递的抽象消息队列契约。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract void start()` | - |
| `public abstract void stop()` | - |
| `public abstract SubscriptionBase subscribe(String topic)` | - |
| `public abstract void unsubscribe(String topic)` | - |
| `public abstract void produceMessage(String topic, QueueMessage message)` | - |
