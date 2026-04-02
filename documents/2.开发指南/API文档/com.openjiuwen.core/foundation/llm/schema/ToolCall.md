# com.openjiuwen.core.foundation.llm.schema.ToolCall

## class ToolCall

```java
public class ToolCall
```

Represents a tool call from LLM output.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | Tool call ID. |
| `type` | `String` | Tool call type (e.g., "function"). |
| `name` | `String` | Tool name. |
| `arguments` | `String` | Tool arguments as JSON string. |
| `index` | `Integer` | Tool call index, used to distinguish multiple tool calls in a single response. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
