# com.openjiuwen.core.foundation.tool.mcp.McpTool

## class McpTool

```java
public class McpTool extends Tool
```

MCP Tool that wraps MCP server tools for LLM function calling.

## Notes

- `invoke(...)` validates inputs against the card schema when present, delegates to `McpClient.callTool(...)`, and wraps the remote result as `{result: ...}`.
- `stream(...)` is intentionally unsupported and throws the shared tool-stream error.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `mcpClient` | `McpClient` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public McpTool(McpClient mcpClient, McpToolCard card)` | Create an MCP tool. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |

## Related Tests

- `McpToolTest`
