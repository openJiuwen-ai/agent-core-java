# com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType

## 枚举 DMessageType

```java
public enum DMessageType
```

DMessageType 定义分布式请求链路中使用的消息类型。

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `INPUT` | 普通请求消息。 |
| `STOP` | 请求远端终止正在执行任务的控制消息。 |
| `OUTPUT` | 服务端返回的结果消息。 |
