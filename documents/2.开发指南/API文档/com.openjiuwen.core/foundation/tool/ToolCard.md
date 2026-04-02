# com.openjiuwen.core.foundation.tool.ToolCard

## class ToolCard

```java
public class ToolCard extends BaseCard
```

Tool metadata record that extends `BaseCard` with JSON-schema input parameters and arbitrary tool properties.

## Notes

- This type relies on Lombok-generated accessors and/or builders; the tables below document the explicit fields declared in source.

## Methods

| Signature | Description |
| --- | --- |
| `public ToolInfo toolInfo()` | Build a `ToolInfo` descriptor for this tool card. |

## Related Tests

- `LocalFunctionTest`, `McpToolTest`, `ToolCardTest`
