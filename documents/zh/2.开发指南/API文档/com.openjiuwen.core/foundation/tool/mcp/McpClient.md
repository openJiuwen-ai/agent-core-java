# com.openjiuwen.core.foundation.tool.mcp.McpClient

## interface McpClient

```java
public interface McpClient
```

MCP 客户端统一接口，定义连接、断开、列出工具、调用工具与查询工具信息的最小能力集。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `boolean connect(int retryTimes, float timeout) throws Exception` | 建立到 MCP 服务端的连接。 |
| `default boolean connect() throws Exception` | 使用默认参数连接，重试 1 次且不设置超时。 |
| `boolean disconnect(float timeout) throws Exception` | 断开连接。 |
| `default boolean disconnect() throws Exception` | 使用无超时参数断开连接。 |
| `List<Object> listTools(float timeout) throws Exception` | 列出服务端暴露的工具元数据。 |
| `default List<Object> listTools() throws Exception` | 使用无超时参数列出工具。 |
| `Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | 按工具名发起调用。 |
| `default Object callTool(String toolName, Map<String, Object> arguments) throws Exception` | 使用无超时参数调用工具。 |
| `Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | 查询单个工具信息。 |
| `default Optional<Object> getToolInfo(String toolName) throws Exception` | 使用无超时参数查询工具信息。 |
| `String getServerPath()` | 返回客户端对应的服务端路径。 |

## 使用说明

- 默认重载全部基于 `McpServerConfig.NO_TIMEOUT`。
- 各实现类可以选择 HTTP、stdio 或本地 OpenAPI 文件等不同传输/数据源形式。
