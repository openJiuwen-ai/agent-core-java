# com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor

## class CallableSchemaExtractor

```java
public final class CallableSchemaExtractor
```

Reflection-based extractor that turns Java method signatures into JSON Schema.

## Notes

- Optional parameters are emitted as nullable schema entries, and non-optional parameters are added to the generated `required` list.

## Constructors

| Signature | Description |
| --- | --- |
| `private CallableSchemaExtractor()` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> generateSchema(Method method)` | - |
| `public static String extractFunctionDescription(Method method)` | - |
| `static String humanizeName(String name)` | - |
