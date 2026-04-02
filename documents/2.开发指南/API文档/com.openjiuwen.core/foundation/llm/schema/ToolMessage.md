# com.openjiuwen.core.foundation.llm.schema.ToolMessage

## class ToolMessage

```java
public class ToolMessage extends BaseMessage
```

Java API page for `ToolMessage`.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `toolCallId` | `String` | The ID of the tool call this message is responding to. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ToolMessage(String content, String toolCallId)` | Create a new `ToolMessage` instance. |
| `public ToolMessage(String content, String toolCallId, String name)` | Create a new `ToolMessage` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getRole()` | Return the `role` value. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
