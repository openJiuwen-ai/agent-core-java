# com.openjiuwen.core.foundation.tool.utils.TypeSchemaExtractor

## class TypeSchemaExtractor

```java
public final class TypeSchemaExtractor
```

Reflection-based schema extraction for Java types.

## Notes

- Collections are mapped to array/object schema shapes, enums are emitted as string enums, and nested POJO fields are expanded recursively until a visited type is seen again.

## Constructors

| Signature | Description |
| --- | --- |
| `private TypeSchemaExtractor()` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> extract(Type type)` | - |
