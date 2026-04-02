# chunker

`com.openjiuwen.core.retrieval.indexing.processor.chunker` 负责把 `Document` 转成可索引的 `TextChunk`，并在必要时执行文本预处理。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`CharChunker`](./chunker/CharChunker.md) | `class` | 固定字符窗口切块。 |
| [`CharSplitterText`](./chunker/CharSplitterText.md) | `class` | 基于字符长度的 `TextSplitter` 实现。 |
| [`Chunker`](./chunker/Chunker.md) | `abstract class` | 面向 `Document -> TextChunk` 的 chunker 抽象。 |
| [`ChunkerRegistry`](./chunker/ChunkerRegistry.md) | `class` | 注册并按名称获取 chunker。 |
| [`HybridChunker`](./chunker/HybridChunker.md) | `class` | 对部分文档跳过切分，其余文档委托内部 chunker。 |
| [`IndexSentenceSplitter`](./chunker/IndexSentenceSplitter.md) | `class` | 包装 `SentenceSplitter` 的 `TextSplitter` 实现。 |
| [`PreprocessingPipeline`](./chunker/PreprocessingPipeline.md) | `class` | 顺序执行多个 `TextPreprocessor`。 |
| [`SpecialCharacterNormalizer`](./chunker/SpecialCharacterNormalizer.md) | `class` | 把控制字符替换为空格。 |
| [`TextChunker`](./chunker/TextChunker.md) | `class` | 先预处理再委托字符或 token chunker。 |
| [`TextPreprocessor`](./chunker/TextPreprocessor.md) | `interface` | 文本预处理器接口。 |
| [`TextSplitter`](./chunker/TextSplitter.md) | `abstract class` | 直接从 `Document` 切分 `TextChunk` 的轻量基类。 |
| [`TokenizerChunker`](./chunker/TokenizerChunker.md) | `class` | 基于 `SentenceSplitter` 的 token-aware chunker。 |
| [`URLEmailRemover`](./chunker/URLEmailRemover.md) | `class` | 删除 URL 和邮箱。 |
| [`WhitespaceNormalizer`](./chunker/WhitespaceNormalizer.md) | `class` | 折叠连续空白并去首尾空格。 |

## 关键行为

- `CharChunkerTest` 验证字符窗口切块支持 overlap，并在 `chunkOverlap >= chunkSize` 时抛错。
- `TokenizerChunkerTest` 和 `SentenceSplitterTest` 验证 token 窗口支持英文 tokenizer、中文自动识别和 overlap 保留上下文。
- `ChunkerRegistry` 默认注册 `char`、`token`、`text`、`hybrid` 四种 chunker 名称。
- `TextChunker` 默认预处理链为 `WhitespaceNormalizer` + `URLEmailRemover`。
