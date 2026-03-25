# operator 模块缺漏复核清单

## 复核口径

- 基线: `agent-core-python/openjiuwen/core/operator/**`
- 对照: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/operator/**`
- 本文只记录“仍未完全对齐的公开 API / 调用语义差异”。
- 以下差异默认不计入缺漏:
  - `snake_case -> camelCase`
  - `property -> getter`
  - `async -> 同步`
  - Python 异常类型到 Java 异常类型的语言适配

## 复核结论

- 当前 Java `operator` 主链路类与主要公开方法已经对齐，未发现新的“整类缺失”或“核心公开方法缺失”。
- 对照此前 `docs/operator_needfix.md` 中列出的旧问题:
  - `LLMCallOperator.updateUserPrompt` 的空字符串语义问题: 已闭环
  - `MemoryCallOperator.stream` 对 streaming 能力的过严判定: 已闭环
  - `legacy.llm_call.LLMCall` 被错误实现为新 operator 别名: 已闭环
  - operator stream 缺少显式关闭协议: 已通过 `OperatorStream.close()` 与各自 `ContextClosingIterator` 补齐
- 仍有 4 个值得继续关注的残余差异，主要集中在流式提前中止清理、duck typing 迁移成本与包级别名语义。

## 剩余差异清单

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | 提前中止的 stream 清理 | Python `async for` / generator close 路径更接近“停止即回收” | Java 只有“读完”或“显式 `close()`”时才会触发 `ContextClosingIterator` 清理 | 调用方若中途停止遍历且忘记 `close()`，`Session.currentOperatorId` 可能短暂残留。 |
| `P1` | duck typing 迁移能力 | Python `MemoryCallOperator`、`ToolCallOperator`、`tool_registry`、legacy callback 都可以直接接收“只要有同名方法”的对象或 callable | Java 必须实现 `MemoryOperation`、`MemoryInvoker`、`Tool`、`ToolRegistry`、`ToolExecutor`、`LegacyOptimizerCallback` 等显式类型 | Python 侧轻量 mock / 动态对象迁移到 Java 时，需要额外适配层。 |
| `P2` | `LLMCall` 的别名语义 | Python `LLMCall = LLMCallOperator`，两者是同一个类型对象 | Java `LLMCall extends LLMCallOperator`，是兼容子类而不是同一类型 | 若上层代码依赖严格类型身份、反射类名或 `getClass()` 判定，行为会与 Python 不完全一致。 |
| `P3` | 包级导出门面 | Python `openjiuwen.core.operator.__all__`、`llm_call.__all__`、`memory_call.__all__`、`tool_call.__all__` 支持包级导入习惯 | Java 无 package facade，只能直接导入具体类 | 迁移文档需要明确导入路径，不能机械照搬 Python 的包级导出用法。 |

## 非缺漏但需要说明的结构适配

- `Operator.stream()`:
  - Python 返回 `AsyncIterator`
  - Java 返回 `OperatorStream<?>`
- `ToolCallOperator` router mode:
  - Python 返回 `List[Tuple[result, tool_message]]`
  - Java 返回 `List<ToolExecutionResult>`
- `TunableSpec`:
  - Python 用 `__slots__`
  - Java 用 `record`
- `tool_registry` / `memory` / callback:
  - Python 靠 duck typing
  - Java 靠显式接口

这些都属于“已映射但采用 Java 化承载方式”，不再计入缺漏。

## 建议优先级

1. 如果后续还要继续追平 Python 运行语义，优先补“提前中止 stream 时的上下文自动回收”。
2. 如果后续重点是降低迁移成本，优先补一层更轻量的 adapter / facade，让 Python 风格的动态对象更容易接入 Java operator。
3. `LLMCall` 真别名语义与包级导出门面都属于低优先级兼容项，可在外部迁移文档中先行说明。
