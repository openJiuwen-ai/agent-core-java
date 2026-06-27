# tool_call

`com.openjiuwen.core.operator.tool_call` 提供工具调用算子、路由模式执行结果和工具注册表契约。

## Types

| 类型 | 说明 |
|---|---|
| [`ToolCallOperator`](./tool_call/ToolCallOperator.md) | 支持直连 `Tool` 或路由式批量 `tool_calls` 执行的调用算子。 |
| [`ToolExecutionResult`](./tool_call/ToolExecutionResult.md) | 路由模式下单个工具调用的结果与 `ToolMessage` 包装。 |
| [`ToolExecutor`](./tool_call/ToolExecutor.md) | 路由模式执行器接口。 |
| [`ToolRegistry`](./tool_call/ToolRegistry.md) | 供算子暴露 `tool_description` tunable 的最小 registry 契约。 |

## Notes

- 只有在注入 `ToolRegistry` 时，`ToolCallOperator` 才会暴露 `tool_description` 可调参数。
- 路由模式与直连模式共用 `enabled` / `max_retries` 状态快照，但返回值形态不同。
