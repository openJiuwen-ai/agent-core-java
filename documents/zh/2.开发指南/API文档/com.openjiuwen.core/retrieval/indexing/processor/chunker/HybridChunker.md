# com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker

## 类 HybridChunker

```java
public class HybridChunker extends Chunker
```

`HybridChunker` 允许某些文档跳过切分，直接以整篇文本生成一个 `TextChunk`；其余文档继续委托内部 chunker。

## 构造方法

### `public HybridChunker(Chunker innerChunker)`

默认对 metadata 中 `source_type` 为 `row` 或 `column` 的文档不做切分。

### `public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen)`

允许自定义“不切分”判定规则。

## 公开方法

- `chunkText(String text)`：直接委托给内部 chunker。
- `chunkDocuments(List<Document> documents)`：命中 `noSplitWhen` 时直接 `TextChunk.fromDocument(...)`，否则走内部 chunker。
