# com.openjiuwen.core.foundation.llm.schema.AssistantMessage

## class AssistantMessage

```java
public class AssistantMessage extends BaseMessage
```

Java API page for `AssistantMessage`.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `toolCalls` | `List<ToolCall>` | Stored `toolCalls` value. |
| `usageMetadata` | `UsageMetadata` | Stored `usageMetadata` value. |
| `finishReason` | `String` | Stored `finishReason` value. |
| `parserContent` | `Object` | Stored `parserContent` value. |
| `reasoningContent` | `String` | Stored `reasoningContent` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public AssistantMessage(String content)` | Create an assistant message with string content. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getRole()` | Return the `role` value. |
| `public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls)` | Convert OpenAI API nested tool_calls format to flat ToolCall format. |
| `public Map<String, Object> toApiFormat()` | Convert this message to OpenAI-compatible dict format for API requests. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
