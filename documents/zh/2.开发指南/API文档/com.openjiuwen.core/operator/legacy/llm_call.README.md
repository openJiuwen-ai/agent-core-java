# llm_call

`com.openjiuwen.core.operator.legacy.llm_call` 保留旧版单次/流式 LLM 调用包装，用于兼容仍依赖 optimizer callback 风格接口的调用方。

## Types

| 类型 | 说明 |
|---|---|
| [`LLMCall`](./llm_call/LLMCall.md) | 旧版 LLM 调用包装，支持 prompt 模板、history 注入与完成回调。 |
| [`LegacyOptimizerCallback`](./llm_call/LegacyOptimizerCallback.md) | 旧版回调接口，在调用完成后接收最终响应对象。 |

## Notes

- 该子包不会像 `LLMCallOperator` 那样写入 `Session.setCurrentOperatorId()`。
- 流式场景会在流结束时把所有 chunk 聚合成字符串，再交给 `LegacyOptimizerCallback`。
