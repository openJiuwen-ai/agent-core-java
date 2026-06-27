# com.openjiuwen.core.common.logging.events.StreamEvent

## 类 StreamEvent

```java
public class StreamEvent extends BaseLogEvent
```

`StreamEvent` 是所有流式事件的公共扩展基类，用于补充流类型、帧计数和分片标识。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `streamType` | `String` | `stream_type` | 流类型。 |
| `chunkIndex` | `Integer` | `chunk_index` | 当前流式片段序号。 |
| `frameCount` | `Integer` | `frame_count` | 流帧数量。 |
| `streamId` | `String` | `stream_id` | 流标识。 |

## 构造与序列化

- 默认构造函数只调用 `super()`，不会覆写 `moduleType`，因此默认值仍为 `ModuleType.SYSTEM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- 内建 `EventClassRegistry` 不直接返回 `StreamEvent`；它主要作为 `WorkflowStreamEvent` 等流式事件的父类。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
