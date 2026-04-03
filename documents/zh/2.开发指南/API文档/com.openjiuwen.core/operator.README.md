# operator

`com.openjiuwen.core.operator` 定义 openJiuwen 的原子算子抽象，以及围绕 LLM、Memory、Tool 调用的具体算子实现和 legacy 兼容层。

## Modules

| 模块 | 说明 |
|---|---|
| [`legacy`](./operator/legacy.README.md) | 旧版 `LLMCall` 兼容入口，保留 optimizer callback 风格回调。 |
| [`llm_call`](./operator/llm_call.README.md) | 基于 `Model` 的 LLM 调用算子与兼容别名。 |
| [`memory_call`](./operator/memory_call.README.md) | 记忆调用算子、记忆调用回调以及最小 memory 契约。 |
| [`tool_call`](./operator/tool_call.README.md) | 工具直连/路由调用算子、执行结果与 registry 契约。 |

## Types

| 类型 | 说明 |
|---|---|
| [`Operator`](./operator/Operator.md) | 所有算子的抽象基类，统一约束 tunable、状态快照、调用与流式接口。 |
| [`OperatorStream`](./operator/OperatorStream.md) | 带显式 `close()` 钩子的迭代式流包装，用于自动清理算子上下文。 |
| [`TunableSpec`](./operator/TunableSpec.md) | 描述单个可调参数的 record，包括类型、路径和约束信息。 |

## Notes

- 本包文档以 `Operator.java`、`LLMCallOperator.java`、`MemoryCallOperator.java`、`ToolCallOperator.java` 及对应测试为依据。
- 根包负责定义统一算子契约，具体的 LLM/Memory/Tool 行为分别放在各自子包中。
