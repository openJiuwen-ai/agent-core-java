# com.openjiuwen.core.foundation.prompt.PromptTemplate

## class PromptTemplate

```java
public class PromptTemplate
```

Interpolatable text prompt template with configurable placeholders.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `String` | `""` | Template name. |
| `content` | `Object` | `""` | Template content - either a plain String or a List . |
| `placeholderPrefix` | `String` | `"{{"` | Left delimiter for placeholders. |
| `placeholderSuffix` | `String` | `"}}"` | Right delimiter for placeholders. |

## Constructors

| Signature | Description |
| --- | --- |
| `public PromptTemplate(String name, Object content, String placeholderPrefix, String placeholderSuffix)` | Four-arg constructor for direct instantiation in tests. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<BaseMessage> toMessages()` | Convert template content to a list of BaseMessages. |
| `public PromptTemplate format(Map<String, Object> keywords)` | Replace placeholders with the given keywords and return a new PromptTemplate. |
| `private static BaseMessage copyMessage(BaseMessage bm)` | Copy a BaseMessage preserving its original subtype. |

## Related Tests

- `PromptAssembleTest`
