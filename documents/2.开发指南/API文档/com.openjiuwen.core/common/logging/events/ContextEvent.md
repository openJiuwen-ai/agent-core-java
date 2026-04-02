# com.openjiuwen.core.common.logging.events.ContextEvent

## 类 ContextEvent

```java
public class ContextEvent extends BaseLogEvent
```

`ContextEvent` 用于记录上下文消息写入、读取和清理过程中的消息属性与容量信息。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `messageType` | `String` | `message_type` | 上下文消息类型。 |
| `messageContent` | `String` | `message_content` | 上下文消息正文。 |
| `messageRole` | `String` | `message_role` | 上下文消息角色。 |
| `contextSize` | `Integer` | `context_size` | 当前上下文条目数。 |
| `maxContextSize` | `Integer` | `max_context_size` | 允许的最大上下文容量。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.CONTEXT`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `CONTEXT_ADD_MESSAGE`、`CONTEXT_CLEAR`、`CONTEXT_RETRIEVE` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `message_content` 做脱敏。
