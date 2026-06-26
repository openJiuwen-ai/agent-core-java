# openjiuwen.core.retrieval.indexing.processor.chunker.chunking

## class TextChunker

文本分块器，支持按字符或按 token 分块，并可配置文本预处理。

```java
new TextChunker()
new TextChunker(int chunkSize, int chunkOverlap)
new TextChunker(int chunkSize, int chunkOverlap, String chunkUnit)
new TextChunker(
    int chunkSize,
    int chunkOverlap,
    String chunkUnit,
    IndexSentenceSplitter.TokenCodec tokenizer,
    TextChunker.PreprocessOptions preprocessOptions
)
```

### 参数

- `chunkSize`: 分块大小，默认 `512`。
- `chunkOverlap`: 分块重叠大小，默认 `50`。
- `chunkUnit`: 分块单位。`"char"` 使用 `CharChunker`；其它值按 token 分块。
- `tokenizer`: token 分块使用的 tokenizer，对应 Python 的 `embed_model.tokenizer`。
- `preprocessOptions`: 预处理选项，支持 `normalizeWhitespace` 和 `removeUrlEmail`。

### chunkDocuments

```java
List<TextChunk> chunkDocuments(List<Document> documents)
```

对文档列表执行预处理和分块，返回 `TextChunk` 列表。每个输出 chunk 保留原始 metadata，并补充：

- `chunk_index`
- `total_chunks`
- `chunk_id`

### getChunker

```java
Chunker getChunker(
    int chunkSize,
    int chunkOverlap,
    String chunkUnit,
    IndexSentenceSplitter.TokenCodec tokenizer
)
```

当 `chunkUnit` 为 `"char"` 时返回 `CharChunker`。其它值返回 `TokenizerChunker`；如果没有 tokenizer，则抛出与 Python token 分块错误对应的 `BaseError`。
