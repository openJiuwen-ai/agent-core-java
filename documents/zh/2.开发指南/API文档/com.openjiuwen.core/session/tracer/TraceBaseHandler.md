# com.openjiuwen.core.session.tracer.TraceBaseHandler

## 抽象类 TraceBaseHandler

```java
public abstract class TraceBaseHandler extends BaseHandler
```

`TraceBaseHandler` 为具体 tracer handler 提供共用能力，包括 trace writer 获取、span 快照发送、耗时格式化与节点状态推导。

## 说明

- 构造时会从 `StreamWriterManager` 取得 `getTraceWriter()`，并保存 `SpanManager`。
- 发送数据时会对 `Span` 调用 `snapshot()`，避免已经发出的 trace 帧被后续更新污染。
- 节点状态会根据 `error`、`onInvokeData` 与 `endTime` 推导为 `start`、`running`、`finish` 或 `error`。
