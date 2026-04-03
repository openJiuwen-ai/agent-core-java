# extract

`com.openjiuwen.core.memory.process.extract` 负责把对话消息转换为长期记忆相关结果，覆盖参数封装、模型分析、摘要生成与片段记忆提取。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`ExtractMemoryParams`](./extract/ExtractMemoryParams.md) | 封装提取流程所需的用户、消息与模型参数。 |
| [`Generator`](./extract/Generator.md) | 协调分析器与提取器，生成变量、摘要和片段记忆单元。 |
| [`LongTermMemoryExtractor`](./extract/LongTermMemoryExtractor.md) | 使用模型和提示词抽取长期片段记忆。 |
| [`MemoryAnalyzer`](./extract/MemoryAnalyzer.md) | 分析消息内容，输出关键信息标记、变量结果与摘要。 |
| [`MemoryAnalyzerResult`](./extract/MemoryAnalyzerResult.md) | 保存分析阶段返回的结构化结果。 |
| [`VariableResult`](./extract/VariableResult.md) | 表示单个变量的键值抽取结果。 |

## 关键行为

- `Generator.genAllMemory(...)` 会先调用 `MemoryAnalyzer`，再根据配置决定是否生成摘要与片段记忆。
- `LongTermMemoryExtractor` 与 `MemoryAnalyzer` 都通过 `PromptApplier` 组装提示词，并默认最多重试 3 次模型调用。
- `ExtractMemoryParams` 与 `MemoryAnalyzerResult`、`VariableResult` 都是面向流程编排的轻量数据对象。
