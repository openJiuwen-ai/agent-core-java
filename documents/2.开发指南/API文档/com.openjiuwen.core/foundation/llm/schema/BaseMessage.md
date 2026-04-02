# com.openjiuwen.core.foundation.llm.schema.BaseMessage

## class BaseMessage

```java
public class BaseMessage
```

Base message class for LLM conversation messages.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `role` | `String` | Message role (system, user, assistant, tool). |
| `content` | `Object` | Message content — either a plain string or a list of content parts. |
| `name` | `String` | Optional name identifier for the message sender. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BaseMessage(String role, String content)` | Create a message with role and string content. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getContentAsString()` | Get content as string. |
| `public List<Object> getContentAsList()` | Get content as list (for multimodal messages). |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
