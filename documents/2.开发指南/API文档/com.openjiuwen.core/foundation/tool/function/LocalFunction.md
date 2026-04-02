# com.openjiuwen.core.foundation.tool.function.LocalFunction

## class LocalFunction

```java
public class LocalFunction extends Tool
```

Local function tool that wraps a Java `Function` as a tool. The wrapped function receives input as a `Map ` and returns the result. Usage:

## Notes

- `invoke(...)` optionally validates inputs through `SchemaUtils.formatWithSchema(...)`, and `stream(...)` only accepts results that are already `Iterator` or `Iterable` instances.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `func` | `Function<Map<String, Object>, Object>` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func)` | Create a local function tool. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |
| `public Function<Map<String, Object>, Object> getFunc()` | Get the underlying function. |

## Related Tests

- `LocalFunctionTest`
