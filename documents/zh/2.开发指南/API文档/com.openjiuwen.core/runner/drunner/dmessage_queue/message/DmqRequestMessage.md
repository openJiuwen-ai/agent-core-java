# com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage

## 类 DmqRequestMessage

```java
public class DmqRequestMessage extends DmqMessage
```

`DmqRequestMessage` 表示分布式远程调用请求消息，封装回复 topic、收发方、流式标记和过期时间。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `DMessageType` | `DMessageType.INPUT` | 请求消息类型，默认是普通输入请求。 |
| `replyTopic` | `String` | `""` | 远端返回响应时使用的 topic。 |
| `requestId` | `String` | `""` | 请求关联标识。 |
| `senderId` | `String` | `""` | 请求发送方标识。 |
| `receiverId` | `String` | `""` | 目标远端标识。 |
| `enableStream` | `boolean` | `-` | 是否按流式模式处理响应。 |
| `expireAt` | `Double` | `-` | 请求过期时间，单位为秒级时间戳。 |
