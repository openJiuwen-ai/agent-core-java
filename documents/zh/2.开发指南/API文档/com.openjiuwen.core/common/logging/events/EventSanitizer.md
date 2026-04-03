# com.openjiuwen.core.common.logging.events.EventSanitizer

## 类 EventSanitizer

```java
public final class EventSanitizer
```

`EventSanitizer` 在日志输出前对结构化事件做字段级脱敏，把敏感值替换为固定占位符 `<REDACTED>`。

## 常量

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `REDACTED` | `"<REDACTED>"` | 脱敏后写回事件映射的统一占位文本。 |
| `DEFAULT_SENSITIVE_FIELDS` | `messages`、`response_content`、`input_content`、`query`、`arguments`、`result`、`message_content`、`tool_calls`、`input_data`、`output_data`、`retrieved_memories` | 默认需要被替换的字段名列表。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event)` | 使用默认敏感字段列表进行脱敏。 |
| `public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event, List<String> sensitiveFields)` | 使用自定义字段列表进行脱敏；传入 `null` 时仍回退到默认列表。 |

## 说明

- 脱敏逻辑会先复制 `event.toMap()` 的结果，再替换命中的键值，因此不会修改原始事件对象。
- 只有当目标键存在且值非 `null` 时才会替换；`StructuredLogEventTest` 覆盖了默认脱敏、自定义脱敏以及 `null` 字段不被误替换的行为。
