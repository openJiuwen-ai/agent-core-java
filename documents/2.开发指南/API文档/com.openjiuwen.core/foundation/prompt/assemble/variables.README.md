# variables

`com.openjiuwen.core.foundation.prompt.assemble.variables` defines reusable variable wrappers for text and structured prompt placeholders.

## Core Types

| Type | Description |
| --- | --- |
| [`DictableVariable`](variables/DictableVariable.md) | Variable class for processing dict or list type placeholders recursively. |
| [`TextableVariable`](variables/TextableVariable.md) | Variable class for processing string-type placeholders. |
| [`Variable`](variables/Variable.md) | Base class for prompt template variables. |

## Notes

- The variable helpers extract placeholder keys up front and then update only the matching inputs passed into `eval(...)` / `update(...)`.
