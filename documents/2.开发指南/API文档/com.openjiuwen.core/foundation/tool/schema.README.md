# schema

`com.openjiuwen.core.foundation.tool.schema` contains LLM-facing schema DTOs used to describe tools and MCP tool metadata.

## Core Types

| Type | Description |
| --- | --- |
| [`McpToolInfo`](schema/McpToolInfo.md) | Extension of `ToolInfo` that also records which MCP server exposes the tool. |
| [`ToolInfo`](schema/ToolInfo.md) | LLM-facing tool descriptor that follows function-calling conventions and carries the tool name, description, and parameter schema. |
