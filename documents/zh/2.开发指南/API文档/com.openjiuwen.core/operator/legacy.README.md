# legacy

`com.openjiuwen.core.operator.legacy` 收纳仍需兼容旧接口形态的算子实现，目前仅包含旧版 `LLMCall` 兼容子包。

## Modules

| 模块 | 说明 |
|---|---|
| [`llm_call`](./legacy/llm_call.README.md) | 旧版 LLM 调用包装与 `LegacyOptimizerCallback` 回调契约。 |

## Notes

- `legacy` 子树用于兼容旧调用面，不继承 `Operator` 抽象，也不维护新的 operator context 约定。
