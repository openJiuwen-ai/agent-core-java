# llm_call

`com.openjiuwen.core.operator.llm_call` 提供可调 prompt 的 LLM 调用算子，以及面向旧命名习惯的兼容别名类。

## Types

| 类型 | 说明 |
|---|---|
| [`LLMCall`](./llm_call/LLMCall.md) | `LLMCallOperator` 的向后兼容别名，保留旧类名。 |
| [`LLMCallOperator`](./llm_call/LLMCallOperator.md) | 基于 `Model` 执行单次或流式 LLM 调用的主算子实现。 |

## Notes

- 构造时的 `userPrompt` 为空字符串或 `null` 时会回退到默认模板 `{{query}}`。
- `history` 与 `tools` 通过 `invoke()/stream()` 的 `kwargs` 传入，其余参数会透传到底层 `Model`。
