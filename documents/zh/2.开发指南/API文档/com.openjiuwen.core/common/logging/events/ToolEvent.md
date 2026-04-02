# com.openjiuwen.core.common.logging.events.ToolEvent

## 类 ToolEvent

```java
public class ToolEvent extends BaseLogEvent
```

`ToolEvent` 承载工具名称、类型、描述、入参、结果和执行耗时。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `toolName` | `String` | `tool_name` | 工具名称。 |
| `toolType` | `String` | `tool_type` | 工具类型。 |
| `toolDescription` | `String` | `tool_description` | 工具描述。 |
| `arguments` | `Map<String, Object>` | `arguments` | 工具入参。 |
| `result` | `Object` | `result` | 工具执行结果。 |
| `executionTimeMs` | `Double` | `execution_time_ms` | 执行耗时，单位毫秒。 |
| `toolCallId` | `String` | `tool_call_id` | 工具调用 ID。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.TOOL`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `TOOL_CALL_START`、`TOOL_CALL_END`、`TOOL_CALL_ERROR` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `arguments`、`result` 做脱敏。
- `StructuredLogEventTest` 覆盖了 `toolName` 和 `arguments` 字段的典型赋值。
