# com.openjiuwen.core.foundation.tool.mcp.McpClient

## interface McpClient

```java
public interface McpClient
```

Abstract MCP client interface for communicating with MCP servers. Implementations (SSE, Stdio, etc.) handle the specific transport protocols.

## Methods

| Signature | Description |
| --- | --- |
| `boolean connect(int retryTimes, float timeout) throws Exception` | Connect to the MCP server. |
| `default boolean connect() throws Exception` | Connect with defaults (1 retry, no timeout). |
| `boolean disconnect(float timeout) throws Exception` | Disconnect from the MCP server. |
| `default boolean disconnect() throws Exception` | Disconnect with no timeout. |
| `List<Object> listTools(float timeout) throws Exception` | List all available tools on the MCP server. |
| `default List<Object> listTools() throws Exception` | List tools with no timeout. |
| `Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | Call a tool on the MCP server. |
| `default Object callTool(String toolName, Map<String, Object> arguments) throws Exception` | Call a tool with no timeout. |
| `Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | Get information about a specific tool. |
| `default Optional<Object> getToolInfo(String toolName) throws Exception` | Get tool info with no timeout. |
| `String getServerPath()` | Get the server path this client is connected to. |

## Related Tests

- `McpToolTest`
