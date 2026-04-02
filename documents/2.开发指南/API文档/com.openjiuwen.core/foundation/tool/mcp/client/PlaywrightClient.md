# com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient

## class PlaywrightClient

```java
public class PlaywrightClient implements McpClient
```

Playwright MCP client that delegates to SSE or stdio depending on the configured server path.

## Notes

- The constructor selects `SseClient` for HTTP server paths and `StdioClient` otherwise.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `delegate` | `McpClient` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public PlaywrightClient(McpServerConfig config)` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | - |
| `public boolean disconnect(float timeout) throws Exception` | - |
| `public List<Object> listTools(float timeout) throws Exception` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | - |
| `public String getServerPath()` | - |
