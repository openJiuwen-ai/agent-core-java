# processor

`com.openjiuwen.core.retrieval.indexing.processor` 提供 indexing 处理链中的基础处理器抽象，并拆分出 chunker、extractor、parser、splitter 四类子能力。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`Processor`](./processor/Processor.md) | `interface` | 通用 `process(input, options)` 处理器接口。 |

## 子包

| 子包 | 说明 |
| --- | --- |
| [`chunker`](./processor/chunker.README.md) | 对 `Document` 做预处理并产出 `TextChunk`。 |
| [`extractor`](./processor/extractor.README.md) | 从 `TextChunk` 中抽取 `Triple`。 |
| [`parser`](./processor/parser.README.md) | 从文件或 URL 解析出 `Document`。 |
| [`splitter`](./processor/splitter.README.md) | 以句子或窗口为单位切分 `Document`。 |
