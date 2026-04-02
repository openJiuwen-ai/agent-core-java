# com.openjiuwen.core.runner.mq.InvokeQueueMessage

## 类 InvokeQueueMessage

```java
public class InvokeQueueMessage extends QueueMessage
```

`InvokeQueueMessage` 用于请求-响应模式，在 `QueueMessage` 基础上额外持有 `CompletableFuture<Object>` 响应对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `response` | `CompletableFuture<Object>` | `new CompletableFuture<>()` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InvokeQueueMessage()` | - |
| `public InvokeQueueMessage(String messageId, Object payload)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public CompletableFuture<Object> getResponse()` | - |

## 相关测试

- `MessageQueueInMemoryTest`
