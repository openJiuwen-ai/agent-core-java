# extractor

`com.openjiuwen.core.retrieval.indexing.processor.extractor` 负责从 `TextChunk` 抽取 `Triple`，既支持本地轻量规则，也支持调用大模型。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`Extractor`](./extractor/Extractor.md) | `abstract class` | 三元组抽取器抽象。 |
| [`LLMTripleExtractor`](./extractor/LLMTripleExtractor.md) | `class` | 使用 `BaseModelClient` 并发调用模型抽取三元组。 |
| [`SimpleTripleExtractor`](./extractor/SimpleTripleExtractor.md) | `class` | 基于句子与空白分词的本地规则抽取器。 |

## 关键行为

- `SimpleTripleExtractorTest` 验证短句会被跳过，抽取结果会在 metadata 中补入 `doc_id` 与 `chunk_id`。
- `LLMTripleExtractorTest` 验证模型返回既可以是数组，也可以是带 `triples` 字段的对象；第四位数字会被解析为 `confidence`。
- `LLMTripleExtractor` 会用虚拟线程并发处理 chunk，并在任一 chunk 解析失败时聚合失败 `chunk_id` 后统一抛出异常。
