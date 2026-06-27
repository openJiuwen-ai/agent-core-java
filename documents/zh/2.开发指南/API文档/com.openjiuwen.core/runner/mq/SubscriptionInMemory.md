# com.openjiuwen.core.runner.mq.SubscriptionInMemory

## 类 SubscriptionInMemory

```java
public class SubscriptionInMemory extends SubscriptionBase
```

`SubscriptionInMemory` 是基于阻塞队列的内存订阅实现；激活后会启动虚拟线程持续消费消息。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `queueMaxSize` | `int` | `-` | - |
| `timeoutMs` | `long` | `-` | - |
| `queue` | `BlockingQueue<QueueMessage>` | `-` | - |
| `active` | `boolean` | `-` | - |
| `handler` | `AsyncMessageHandler<Object, Object>` | `-` | - |
| `consumerExecutor` | `ExecutorService` | `-` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SubscriptionInMemory(int maxSize, long timeoutMs)` | - |
| `public SubscriptionInMemory()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setMessageHandler(AsyncMessageHandler<Object, Object> handler)` | - |
| `public void activate()` | - |
| `public void deactivate()` | - |
| `public boolean isActive()` | - |
| `public void pushMessage(QueueMessage message)` | - |
