# com.openjiuwen.core.runner.drunner.server_adapter.MqMessageUtils

## 类 MqMessageUtils

```java
public final class MqMessageUtils
```

`MqMessageUtils` 提供构造分布式 MQ 响应消息的辅助方法。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static DmqResponseMessage buildStreamResponse(DmqRequestMessage request, String senderId, Object payload, int seq, boolean last)` | 构造一个流式响应分片。 |
| `public static DmqResponseMessage buildFinalResponse(DmqRequestMessage request, String senderId, int seq)` | 构造表示流结束的最终响应分片。 |
| `public static DmqResponseMessage buildBatchResponse(DmqRequestMessage request, String senderId, Object result)` | 构造一次性调用的正常结果响应。 |
| `public static DmqResponseMessage buildErrorResponse(DmqRequestMessage request, String senderId, Exception error)` | 构造错误响应，并在可用时写入 `BaseError` 的错误码。 |
