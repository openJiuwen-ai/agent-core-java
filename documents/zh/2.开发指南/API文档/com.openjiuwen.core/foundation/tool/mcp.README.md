# mcp

`com.openjiuwen.core.foundation.tool.mcp` 提供与 MCP 服务端交互的客户端协议、配置对象、工具卡片，以及把远端 MCP 工具包装为本地 `Tool` 的实现。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`client`](tool/mcp/client.README.md) | 提供基于 HTTP JSON-RPC、SSE、stdio、OpenAPI 文件等形式的客户端实现。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`McpClient`](mcp/McpClient.md) | MCP 客户端统一接口。 |
| [`McpServerConfig`](mcp/McpServerConfig.md) | MCP 服务端连接配置。 |
| [`McpTool`](mcp/McpTool.md) | 远端 MCP 工具的本地包装器。 |
| [`McpToolCard`](mcp/McpToolCard.md) | 带服务端标识的工具卡片。 |

## 关键行为

- `McpTool.invoke(...)` 会先根据 `inputParams` 格式化输入，再调用 `McpClient.callTool(...)`。
- `McpTool.stream(...)` 明确不支持流式调用，直接抛出 `TOOL_STREAM_NOT_SUPPORTED`。
- `McpToolTest` 覆盖了配置默认值、卡片导出与客户端委托行为。

## 相关测试

- `McpToolTest`
