# com.openjiuwen.core.foundation.tool.schema.ToolInfo

## class ToolInfo

```java
public class ToolInfo
```

LLM-facing tool descriptor that follows function-calling conventions and carries the tool name, description, and parameter schema.

## Notes

- This type relies on Lombok-generated accessors and/or builders; the tables below document the explicit fields declared in source.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `type` | `String` | `"function"` | Tool type, defaults to "function". */ |
| `name` | `String` | `""` | Tool name. */ |
| `description` | `String` | `""` | Tool description. */ |

## Related Tests

- `LocalFunctionTest`, `McpToolTest`, `RestfulApiTest`, `ToolCardTest`
