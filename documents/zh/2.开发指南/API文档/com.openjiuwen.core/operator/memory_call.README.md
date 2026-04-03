# memory_call

`com.openjiuwen.core.operator.memory_call` 定义记忆调用算子，以及记忆对象和自定义回调所需的最小契约。

## Types

| 类型 | 说明 |
|---|---|
| [`MemoryCallOperator`](./memory_call/MemoryCallOperator.md) | 带启用开关、重试状态和可选自定义回调的记忆调用算子。 |
| [`MemoryInvoker`](./memory_call/MemoryInvoker.md) | 用于接入非标准记忆调用流程的回调接口。 |
| [`MemoryOperation`](./memory_call/MemoryOperation.md) | `MemoryCallOperator` 依赖的最小 memory 契约，支持可选流式输出。 |

## Notes

- `MemoryInvoker` 存在时优先于 `MemoryOperation` 执行，适合桥接不遵循统一接口的记忆组件。
- `max_retries` 会在写入状态时被限制到 `[0, 5]` 区间。
