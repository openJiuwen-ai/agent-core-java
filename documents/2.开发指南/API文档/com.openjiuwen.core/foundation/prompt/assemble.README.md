# assemble

`com.openjiuwen.core.foundation.prompt.assemble` contains the runtime assembler that resolves placeholders across prompt payloads.

## Modules

| Module | Description |
| --- | --- |
| [`variables`](assemble/variables.README.md) | text and structured-variable helpers that power placeholder replacement. |

## Core Types

| Type | Description |
| --- | --- |
| [`PromptAssembler`](assemble/PromptAssembler.md) | Assembler that substitutes placeholders in a prompt template. |

## Notes

- `PromptAssembler` accepts either raw strings or `List<BaseMessage>` content and preserves unresolved placeholders when callers omit keys.
