# com.openjiuwen.core.runner.mq.StreamQueueMessage

## 类 StreamQueueMessage

```java
public class StreamQueueMessage extends QueueMessage
```

`StreamQueueMessage` 用于流式响应模式，在 `QueueMessage` 基础上额外持有 `CompletableFuture<Iterator<Object>>` 响应对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `response` | `CompletableFuture<Iterator<Object>>` | `new CompletableFuture<>()` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamQueueMessage()` | - |
| `public StreamQueueMessage(String messageId, Object payload)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public CompletableFuture<Iterator<Object>> getResponse()` | - |

## 相关测试

- `MessageQueueInMemoryTest`
