# com.openjiuwen.core.foundation.tool.mcp.McpServerConfig

## class McpServerConfig

```java
public class McpServerConfig
```

Connection settings for an MCP server endpoint or process, including auth headers, auth query parameters, and transport-specific params.

## Notes

- This type relies on Lombok-generated accessors and/or builders; the tables below document the explicit fields declared in source.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `serverName` | `String` | `-` | Server display name. */ |
| `serverPath` | `String` | `-` | Server path or URL. */ |
| `clientType` | `String` | `"sse"` | Client type (e.g., "sse", "stdio"). */ |
| `NO_TIMEOUT` | `float` | `-1` | Constant for no timeout. */ |

## Related Tests

- `McpToolTest`
