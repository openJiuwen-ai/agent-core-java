# com.openjiuwen.core.foundation.llm.schema.BaseMessageChunk

## class BaseMessageChunk

```java
public class BaseMessageChunk extends BaseMessage
```

Java API page for `BaseMessageChunk`.

## Constructors

| Signature | Description |
| --- | --- |
| `public BaseMessageChunk(String role, Object content, String name)` | Create a new `BaseMessageChunk` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public BaseMessageChunk merge(BaseMessageChunk other)` | Merge another chunk into this one (content concatenation). |
| `protected static Object mergeContent(Object left, Object right)` | Merge content fields based on type compatibility. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
