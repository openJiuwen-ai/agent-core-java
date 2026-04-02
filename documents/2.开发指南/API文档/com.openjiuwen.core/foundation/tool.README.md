# tool

`com.openjiuwen.core.foundation.tool` provides the core tool abstractions, metadata cards, and adapters for local functions, MCP servers, REST APIs, and schema extraction utilities.

## Modules

| Module | Description |
| --- | --- |
| [`annotation`](tool/annotation.README.md) | contains annotation markers used to expose Java methods as tool definitions. |
| [`function`](tool/function.README.md) | contains factories and wrappers for exposing in-process Java callables as tool instances. |
| [`mcp`](tool/mcp.README.md) | contains contracts and runtime wrappers for invoking tools exposed by MCP servers. |
| [`schema`](tool/schema.README.md) | contains LLM-facing schema DTOs used to describe tools and MCP tool metadata. |
| [`service_api`](tool/service_api.README.md) | contains REST adapter types that map structured tool inputs onto HTTP requests and normalized responses. |
| [`utils`](tool/utils.README.md) | contains reflection helpers that derive JSON Schema from Java methods and types. |

## Core Types

| Type | Description |
| --- | --- |
| [`Tool`](tool/Tool.md) | Abstract base class for all tools. Defines the contract for tool invocation and streaming. Usage: |
| [`ToolCard`](tool/ToolCard.md) | Tool metadata record that extends `BaseCard` with JSON-schema input parameters and arbitrary tool properties. |

## Notes

- `ToolCardTest`, `LocalFunctionTest`, `McpToolTest`, `RestfulApiTest`, `ApiParamMapperTest`, and `ResponseParserTest` cover the main tool metadata and adapter flows.
