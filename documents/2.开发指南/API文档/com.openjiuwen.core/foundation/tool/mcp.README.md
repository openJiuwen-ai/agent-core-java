# mcp

`com.openjiuwen.core.foundation.tool.mcp` contains contracts and runtime wrappers for invoking tools exposed by MCP servers.

## Modules

| Module | Description |
| --- | --- |
| [`client`](mcp/client.README.md) | contains concrete MCP client transports for HTTP, SSE, stdio, Playwright, and OpenAPI-backed integrations. |

## Core Types

| Type | Description |
| --- | --- |
| [`McpClient`](mcp/McpClient.md) | Abstract MCP client interface for communicating with MCP servers. Implementations (SSE, Stdio, etc.) handle the specific transport protocols. |
| [`McpServerConfig`](mcp/McpServerConfig.md) | Connection settings for an MCP server endpoint or process, including auth headers, auth query parameters, and transport-specific params. |
| [`McpTool`](mcp/McpTool.md) | MCP Tool that wraps MCP server tools for LLM function calling. |
| [`McpToolCard`](mcp/McpToolCard.md) | Tool metadata that extends `ToolCard` with the MCP server name and server identifier used to resolve remote tool calls. |

## Notes

- `McpToolTest` covers config defaults, MCP card metadata, client delegation, and the non-streaming guard path.
