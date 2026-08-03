# com.openjiuwen.core.foundation.tool.mcp.client.McpClients

## class McpClients

```java
public final class McpClients
```

`McpClients` 是 MCP 客户端注册门面，负责把 Java SDK 内置的 MCP transport client 注册到全局 `ClientRegistry`。它用于补齐 Python 版 `BaseClient.__init_subclass__` 的自动注册语义，让 `ResourceMgr`、`ToolManager` 和直接使用 `ClientRegistry` 的代码都可以按 `clientType` 动态创建 MCP client。

## 主要方法

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `registerDefaults()` | `void` | 幂等注册 SDK 内置 MCP client 工厂。 |
| `normalizeClientType(String clientType)` | `String` | 归一化用户传入的 `clientType`，用于 registry 查询。 |

## 默认注册项

| `ClientRegistry` key | 实现类 |
| --- | --- |
| `mcp_sse` | `SseClient` |
| `mcp_stdio` | `StdioClient` |
| `mcp_streamable-http` | `StreamableHttpClient` |
| `mcp_streamable_http` | `StreamableHttpClient` |
| `mcp_openapi` | `OpenApiClient` |
| `mcp_playwright` | `PlaywrightClient` |

## `clientType` 归一化

| 输入示例 | 归一化结果 |
| --- | --- |
| `null`、空字符串 | `sse` |
| `SSE`、`mcp_SSE` | `sse` |
| `stdio`、`mcp_stdio` | `stdio` |
| `streamable_http`、`streamableHttp`、`STREAMABLE_HTTP` | `streamable-http` |
| `mcp_streamable_http`、`mcp-streamable-http` | `streamable-http` |
| `open-api` | `openapi` |
| `playwright` | `playwright` |

## 使用说明

- `ResourceMgr` 与 `ToolManager` 的 MCP Server 注册路径会确保默认 MCP client 已注册。
- 如果业务代码直接通过 `ClientRegistry.getClient(name, "mcp", kwargs)` 创建 MCP client，应先调用 `McpClients.registerDefaults()`。
- MCP client 工厂要求 `kwargs` 中包含 `config`，且值必须是 `McpServerConfig`。

## 相关测试

- `McpClientsTest`
- `ToolManagerTest`
- `ResourceMgrTest`
