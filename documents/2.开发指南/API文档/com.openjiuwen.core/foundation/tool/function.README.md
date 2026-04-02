# function

`com.openjiuwen.core.foundation.tool.function` contains factories and wrappers for exposing in-process Java callables as tool instances.

## Core Types

| Type | Description |
| --- | --- |
| [`AnnotatedToolFactory`](function/AnnotatedToolFactory.md) | Factory that turns `ToolDefinition`-annotated methods into `LocalFunction`s. |
| [`LocalFunction`](function/LocalFunction.md) | Local function tool that wraps a Java `Function` as a tool. The wrapped function receives input as a `Map ` and returns the result. Usage: |

## Notes

- `LocalFunctionTest` covers card validation, synchronous invocation, and iterator-based streaming behavior.
