# com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient

## class PlaywrightClient

```java
public class PlaywrightClient implements McpClient
```

Playwright MCP 客户端门面。它根据 `McpServerConfig.serverPath` 的前缀，在 `SseClient` 与 `StdioClient` 之间自动选择实际委托对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `delegate` | `McpClient` | `-` | 实际承担调用的客户端。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PlaywrightClient(McpServerConfig config)` | 当 `serverPath` 以 `http` 开头时使用 `SseClient`，否则使用 `StdioClient`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | 委托到底层客户端。 |
| `public boolean disconnect(float timeout) throws Exception` | 委托到底层客户端。 |
| `public List<Object> listTools(float timeout) throws Exception` | 委托到底层客户端。 |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | 委托到底层客户端。 |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | 委托到底层客户端。 |
| `public String getServerPath()` | 返回底层客户端的服务端路径。 |
