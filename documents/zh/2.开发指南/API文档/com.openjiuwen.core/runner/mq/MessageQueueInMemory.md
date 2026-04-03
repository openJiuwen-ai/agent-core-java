# com.openjiuwen.core.runner.mq.MessageQueueInMemory

## 类 MessageQueueInMemory

```java
public class MessageQueueInMemory extends MessageQueueBase
```

`MessageQueueInMemory` 提供基于内存主题路由的消息队列实现，内部使用阻塞队列与单线程消费者转发消息。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `queueMaxSize` | `int` | `-` | - |
| `timeoutMs` | `long` | `-` | - |
| `running` | `boolean` | `-` | - |
| `subscribers` | `Map<String, SubscriptionInMemory>` | `new ConcurrentHashMap<>()` | - |
| `queue` | `BlockingQueue<TopicMessage>` | `-` | - |
| `consumerExecutor` | `ExecutorService` | `-` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MessageQueueInMemory(int queueMaxSize, long timeoutMs)` | - |
| `public MessageQueueInMemory()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void start()` | - |
| `public void stop()` | - |
| `public SubscriptionBase subscribe(String topic)` | - |
| `public void unsubscribe(String topic)` | - |
| `public void produceMessage(String topic, QueueMessage message)` | - |

## 相关测试

- `MessageQueueInMemoryTest`
