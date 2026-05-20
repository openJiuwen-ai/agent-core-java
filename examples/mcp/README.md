# MCP Examples Baseline

This directory mirrors the Python `examples/mcp` structure with Java-side baseline examples for:

- `openapi`
- `sse`
- `stdio`
- `streamable_http`
- `playwright`

Each example demonstrates how to build a corresponding `McpServerConfig`.

Suggested verification:

```bash
mvn -Dtest=McpExampleSupportTest test
```
