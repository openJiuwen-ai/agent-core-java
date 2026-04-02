# splitter

`com.openjiuwen.core.retrieval.indexing.processor.splitter` 提供直接从 `Document` 生成 `TextChunk` 的切分能力，重点关注句子边界和 token 窗口控制。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`SentenceSplitter`](./splitter/SentenceSplitter.md) | `class` | 句子感知、支持中英文自动识别的 splitter。 |
| [`Splitter`](./splitter/Splitter.md) | `abstract class` | `Document -> TextChunk` 的切分抽象。 |

## 关键行为

- `Splitter` 会为每个产出的 `TextChunk` 补充 `chunk_index`、`total_chunks` 与 `chunk_id` metadata。
- `SentenceSplitterTest` 验证中文标点不会依赖空格分句，英文场景可通过外部 tokenizer 控制 token 计数。
