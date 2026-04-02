# process

`com.openjiuwen.core.memory.process` 汇总记忆处理流程相关子包；当前任务范围内的公开能力主要来自 `extract` 子包。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`extract`](./process/extract.README.md) | 提供消息分析、长期记忆提取以及提取结果参数模型。 |

## 使用说明

- `LongTermMemory` 在生成记忆单元时会调用 `extract` 子包中的分析器与生成器。
- 该页作为父级导航页，帮助从 `memory` README 跳转到 `extract` 范围文档。
