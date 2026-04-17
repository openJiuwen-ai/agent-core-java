# message

`com.openjiuwen.core.runner.drunner.dmessage_queue.message` 定义分布式请求与响应消息模型及其类型枚举。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`DMessageType`](message/DMessageType.md) | 分布式消息类型枚举。 |
| [`DmqMessage`](message/DmqMessage.md) | 分布式 Runner 队列消息基类。 |
| [`DmqRequestMessage`](message/DmqRequestMessage.md) | 表示远程调用请求消息，携带回复 topic、收发方和过期时间等信息。 |
| [`DmqResponseMessage`](message/DmqResponseMessage.md) | 表示远程调用响应消息，携带结果类型、分片序号和结束标记。 |
| [`ResultType`](message/ResultType.md) | 远程返回结果类型枚举。 |
