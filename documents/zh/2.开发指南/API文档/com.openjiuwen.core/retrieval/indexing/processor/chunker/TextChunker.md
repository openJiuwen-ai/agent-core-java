# com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker

## 类 TextChunker

```java
public class TextChunker extends Chunker
```

`TextChunker` 在真正切块前先做文本清洗，然后把工作委托给字符或 token chunker。

## 构造方法

### `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit)`

默认语言为 `auto`，不显式传 tokenizer。

### `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit, Function<String, List<String>> tokenizer, String language)`

- `chunkUnit == "char"` 时内部使用 `CharChunker`。
- 其他值统一回退到 `TokenizerChunker`。
- 默认预处理链固定为 `WhitespaceNormalizer` 和 `URLEmailRemover`。

## 公开方法

- `chunkText(String text)`：先预处理，再委托内部 chunker。
- `chunkDocuments(List<Document> documents)`：会先把每个 `Document` 的正文替换为预处理结果，再继续切块。
