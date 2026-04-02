# client

`com.openjiuwen.core.foundation.tool.mcp.client` contains concrete MCP client transports for HTTP, SSE, stdio, Playwright, and OpenAPI-backed integrations.

## Core Types

| Type | Description |
| --- | --- |
| [`OpenApiClient`](client/OpenApiClient.md) | OpenAPI-file backed MCP-style client. Parses OpenAPI spec files (JSON/YAML) and converts each route into an MCP tool card with proper parameter schemas, descriptions, and output schemas. |
| [`PlaywrightClient`](client/PlaywrightClient.md) | Playwright MCP client that delegates to SSE or stdio depending on the configured server path. |
| [`SseClient`](client/SseClient.md) | Java baseline SSE MCP client. Current implementation uses HTTP JSON-RPC requests to the configured endpoint, which is sufficient for MCP servers exposing SSE-compatible RPC endpoints. |
| [`StdioClient`](client/StdioClient.md) | Stdio transport MCP client using content-length framed JSON-RPC. |
| [`StreamableHttpClient`](client/StreamableHttpClient.md) | HTTP JSON-RPC based MCP client for streamable-http servers. |

## Notes

- `AbstractHttpMcpClient` is internal and is documented here only through the public transport classes that build on it.
