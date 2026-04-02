# com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage

## 类 DmqResponseMessage

```java
public class DmqResponseMessage extends DmqMessage
```

`DmqResponseMessage` 表示分布式远程调用响应消息，携带结果类型、分片序号和结束标记。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `DMessageType` | `DMessageType.OUTPUT` | 响应消息类型，默认是输出消息。 |
| `resultType` | `ResultType` | `ResultType.MESSAGE` | 响应承载的是正常结果还是错误结果。 |
| `requestId` | `String` | `""` | 与请求对应的关联标识。 |
| `senderId` | `String` | `""` | 响应发送方标识。 |
| `receiverId` | `String` | `""` | 响应接收方标识。 |
| `seq` | `int` | `-` | 流式响应中的分片序号。 |
| `lastChunk` | `boolean` | `-` | 是否为最后一个响应分片。 |
| `expireAt` | `Double` | `-` | 响应过期时间，单位为秒级时间戳。 |
