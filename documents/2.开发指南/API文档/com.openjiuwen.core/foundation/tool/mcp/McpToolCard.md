# com.openjiuwen.core.foundation.tool.mcp.McpToolCard

## class McpToolCard

```java
public class McpToolCard extends ToolCard
```

Tool metadata that extends `ToolCard` with the MCP server name and server identifier used to resolve remote tool calls.

## Notes

- This type relies on Lombok-generated accessors and/or builders; the tables below document the explicit fields declared in source.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `serverName` | `String` | `-` | Server name this tool belongs to. */ |
| `serverId` | `String` | `""` | Server identifier. */ |

## Methods

| Signature | Description |
| --- | --- |
| `public McpToolInfo toolInfo()` | - |

## Related Tests

- `McpToolTest`
