# com.openjiuwen.core.foundation.tool.mcp.client.StdioClient

## class StdioClient

```java
public class StdioClient implements McpClient
```

Stdio transport MCP client using content-length framed JSON-RPC.

## Notes

- The stdio transport can use `params.command`, `params.args`, `params.env`, and `params.cwd` from `McpServerConfig` to launch the target MCP process.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `config` | `McpServerConfig` | `-` | - |
| `process` | `Process` | `-` | - |
| `stdout` | `BufferedInputStream` | `-` | - |
| `stdin` | `BufferedOutputStream` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public StdioClient(McpServerConfig config)` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | - |
| `public boolean disconnect(float timeout) throws Exception` | - |
| `public List<Object> listTools(float timeout) throws Exception` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | - |
| `public String getServerPath()` | - |
