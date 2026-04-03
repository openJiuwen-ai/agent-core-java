# stream

`com.openjiuwen.core.session.stream` 提供流式 schema、发射器、阻塞队列、writer 以及多模式 writer 管理器。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AsyncStreamQueue`](./stream/AsyncStreamQueue.md) | 线程安全的阻塞流队列。 |
| [`CustomSchema`](./stream/CustomSchema.md) | 允许携带任意属性的自定义流 schema。 |
| [`OutputSchema`](./stream/OutputSchema.md) | 框架标准输出流 schema。 |
| [`StreamEmitter`](./stream/StreamEmitter.md) | 负责向流队列推送数据的发射器。 |
| [`StreamMode`](./stream/StreamMode.md) | 流模式枚举。 |
| [`StreamSchema`](./stream/StreamSchema.md) | 流 schema 的标记接口。 |
| [`StreamWriter`](./stream/StreamWriter.md) | 负责校验并发出流数据的 writer。 |
| [`StreamWriterManager`](./stream/StreamWriterManager.md) | 负责不同流模式 writer 的装配与消费。 |
| [`TraceSchema`](./stream/TraceSchema.md) | trace 流 schema。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`。
