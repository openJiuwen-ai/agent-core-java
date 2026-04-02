# com.openjiuwen.core.foundation.tool.mcp.client.SseClient

## class SseClient

```java
public class SseClient extends AbstractHttpMcpClient
```

Java baseline SSE MCP client. Current implementation uses HTTP JSON-RPC requests to the configured endpoint, which is sufficient for MCP servers exposing SSE-compatible RPC endpoints.

## Notes

- This transport inherits JSON-RPC request handling from the internal `AbstractHttpMcpClient` base class.

## Constructors

| Signature | Description |
| --- | --- |
| `public SseClient(McpServerConfig config)` | - |
