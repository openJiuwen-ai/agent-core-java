# foundation

`com.openjiuwen.core.foundation` groups the low-level model, prompt templating, storage, and tool APIs that higher-level openJiuwen modules build on.

## Modules

| Module | Description |
| --- | --- |
| [`llm`](foundation/llm.README.md) | provider-backed model clients, output parsers, and message/config schema types. |
| [`prompt`](foundation/prompt.README.md) | prompt templates, placeholder assembly, and variable wrappers for prompt construction. |
| [`store`](foundation/store.README.md) | vector, graph, KV, object, and database-backed storage abstractions plus adapters. |
| [`tool`](foundation/tool.README.md) | tool cards, local functions, MCP adapters, REST adapters, and schema extraction utilities. |

## Notes

- The documented foundation subtree currently covers `llm`, `prompt`, `store`, and `tool`.
