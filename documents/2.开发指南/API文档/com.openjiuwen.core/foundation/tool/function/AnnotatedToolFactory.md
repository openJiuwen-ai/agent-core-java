# com.openjiuwen.core.foundation.tool.function.AnnotatedToolFactory

## class AnnotatedToolFactory

```java
public final class AnnotatedToolFactory
```

Factory that turns `ToolDefinition`-annotated methods into `LocalFunction`s.

## Notes

- When `autoExtract` is enabled, the factory uses `CallableSchemaExtractor` to derive a JSON Schema from the annotated Java method signature.

## Constructors

| Signature | Description |
| --- | --- |
| `private AnnotatedToolFactory()` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public static List<LocalFunction> scan(Object target)` | - |
| `public static LocalFunction fromMethod(Object target, Method method)` | - |
