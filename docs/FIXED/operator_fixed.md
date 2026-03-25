# operator 模块第二轮复核缺漏清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\operator`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\operator`
- 本文只记录第二轮复核后，Java 相对 Python 仍未完全追平的公开 API / 可见语义差异。

## 第二轮复核结论

- 当前未发现新的“整类缺失”或“核心公开方法缺失”。
- 第一轮已登记的问题里，下面 4 项已经确认关闭:
  - `LLMCallOperator.updateUserPrompt("")` 的空字符串语义已修正
  - `MemoryCallOperator.stream` 已不再错误依赖 `supportsStream()`
  - `legacy.llm_call.LLMCall` 已恢复为独立旧接口兼容实现
  - Java 已提供 `OperatorStream.close()` 与对应的上下文清理包装器
- 因此，第二轮剩余问题主要不是“没实现”，而是“还没有做到与 Python 完全等价”。

## 第二轮仍缺 / 未完全对齐部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `OperatorStream` 提前中止清理 | `async for` 中途退出时，generator 更容易被关闭并回收 operator context | ~~已通过 `OperatorStream.wrap()` + `java.lang.ref.Cleaner` 安全网实现 GC 驱动的自动清理，同时增加了迭代异常时的清理保障~~ | ~~已修复：提前放弃遍历时 Cleaner 保障 `Session.currentOperatorId` 被清理~~ |
| `P1` | duck typing 接入能力 | `MemoryCallOperator`、`ToolCallOperator`、`tool_registry`、legacy callback 都可直接接收“只要有同名方法”的对象或 callable | Java 必须实现 `MemoryOperation`、`MemoryInvoker`、`ToolRegistry`、`ToolExecutor`、`LegacyOptimizerCallback` 等显式类型 | Python 风格 mock / 动态对象迁移到 Java 时，需要补 adapter，迁移成本更高。 |
| `P2` | `LLMCall` 真别名语义 | `LLMCall = LLMCallOperator`，两者是同一个类型对象 | Java `LLMCall extends LLMCallOperator`，属于兼容子类而不是真别名 | 若上层依赖严格类型身份、类名反射、`getClass()` 或 `instanceof` 细节，行为与 Python 不完全一致。 |
| `P3` | 包级导出门面 | Python 通过 `openjiuwen.core.operator.__all__` 以及子包 `__all__` 支持包级导入 | Java 无 package facade，只能直接导入具体类 | 迁移示例与使用方式不能按 Python 导入路径机械替换。 |

## 第二轮新增关注点

以下差异本轮确认存在，但更适合记为“语义差异”而不是“缺漏”:

- Java `LLMCallOperator.invoke()/stream()` 会把 `history/tools` 之外的 kwargs 继续透传给 `Model`；Python 当前实现没有这一层透传。这是 Java 的增强，不算缺漏。
- Java 统一使用同步 `invoke()` 与 `OperatorStream` 承接 Python `async/await` 和 `AsyncIterator`，属于语言运行时模型差异。

## 建议优先级

1. ~~优先补 `OperatorStream` 在"提前放弃消费"场景下的自动清理保障，避免 operator context 残留。~~ **已完成**：通过 `OperatorStream.wrap(Iterator, Runnable)` 静态工厂 + `java.lang.ref.Cleaner` 安全网实现，同时统一了三个 operator 中的重复 `ContextClosingIterator` 代码。
2. 如果后续重点是降低 Python 到 Java 的迁移成本，建议补一层更轻量的 facade / adapter，使动态对象更容易接入 `MemoryCallOperator` 与 `ToolCallOperator`。
3. `LLMCall` 真别名语义与包级导出门面属于低优先级兼容项，可在迁移文档中先明确说明。
