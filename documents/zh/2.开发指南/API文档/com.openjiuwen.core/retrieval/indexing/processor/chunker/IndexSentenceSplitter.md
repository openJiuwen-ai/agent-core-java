# com.openjiuwen.core.retrieval.indexing.processor.chunker.IndexSentenceSplitter

## 类 IndexSentenceSplitter

```java
public class IndexSentenceSplitter extends TextSplitter
```

`IndexSentenceSplitter` 是 `SentenceSplitter` 的包装类，方便在 indexing 流程中以 `TextSplitter` 形式使用句子切分能力。

## 构造方法

### `public IndexSentenceSplitter()`

默认使用 `chunkSize = 200`、自动 overlap、语言 `auto`。

### `public IndexSentenceSplitter(Function<String, List<String>> tokenizer, Integer chunkSize, Integer chunkOverlap, Map<String, Object> splitterConfig, String language)`

- `chunkOverlap == null` 时，默认取 `chunkSize / 5`。
- `splitterConfig` 当前保留但未实际使用。

## 公开方法

### `public List<TextChunk> split(Document doc)`

内部委托 `SentenceSplitter.getNodesFromDocuments(List.of(doc))`。
