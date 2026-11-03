# client

`com.openjiuwen.core.foundation.tool.mcp.client` 提供多种 MCP 客户端实现，包括基于 HTTP JSON-RPC、SSE、stdio，以及从 OpenAPI 规范派生工具的客户端。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`OpenApiClient`](client/OpenApiClient.md) | 从本地 OpenAPI JSON/YAML 文件生成工具卡片并可直接发起 HTTP 调用。 |
| [`PlaywrightClient`](client/PlaywrightClient.md) | 按 `serverPath` 自动在 `SseClient` 与 `StdioClient` 间选择委托实现。 |
| [`SseClient`](client/SseClient.md) | 基于 `AbstractHttpMcpClient` 的 SSE 风格客户端。 |
| [`StdioClient`](client/StdioClient.md) | 通过 `Content-Length` 分帧 JSON-RPC 与本地进程通信。 |
| [`StreamableHttpClient`](client/StreamableHttpClient.md) | 面向 streamable-http 服务端的 HTTP JSON-RPC 客户端。 |
| [`McpClients`](client/McpClients.md) | MCP 客户端注册门面，用于把默认 transport 注册到全局 `ClientRegistry`。 |

## 注册方式

Java 版没有 Python `BaseClient.__init_subclass__` 形式的类定义时自动注册机制。SDK 会通过 `McpClients.registerDefaults()` 显式注册 MCP 客户端工厂，使 `ResourceMgr` 与 `ToolManager` 可以继续按 `McpServerConfig.clientType` 走全局 `ClientRegistry` 创建客户端。

默认注册项包括：

| `ClientRegistry` key | 实现 |
| --- | --- |
| `mcp_sse` | `SseClient` |
| `mcp_stdio` | `StdioClient` |
| `mcp_streamable-http` | `StreamableHttpClient` |
| `mcp_streamable_http` | `StreamableHttpClient` |
| `mcp_openapi` | `OpenApiClient` |
| `mcp_playwright` | `PlaywrightClient` |

## 说明

- `AbstractHttpMcpClient` 是包内抽象基类，不作为任务要求的公开文档页输出。
- `OpenApiClient` 既能列出工具，也能根据 OpenAPI 路径参数和请求体定义直接执行 HTTP 调用。
- `ResourceMgr` 与 `ToolManager` 会在 MCP Server 注册路径上确保默认 MCP client 已注册；直接使用 `ClientRegistry` 时，可以先调用 `McpClients.registerDefaults()`。
