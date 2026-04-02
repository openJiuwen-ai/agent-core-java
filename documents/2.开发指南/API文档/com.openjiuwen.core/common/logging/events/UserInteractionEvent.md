# com.openjiuwen.core.common.logging.events.UserInteractionEvent

## 类 UserInteractionEvent

```java
public class UserInteractionEvent extends BaseLogEvent
```

`UserInteractionEvent` 用于记录用户输入与反馈内容。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `userId` | `String` | `user_id` | 用户标识。 |
| `inputContent` | `String` | `input_content` | 用户输入内容。 |
| `feedbackType` | `String` | `feedback_type` | 反馈类型。 |
| `feedbackContent` | `String` | `feedback_content` | 反馈内容。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.USER`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `USER_INPUT` 和 `USER_FEEDBACK` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `input_content` 做脱敏。
