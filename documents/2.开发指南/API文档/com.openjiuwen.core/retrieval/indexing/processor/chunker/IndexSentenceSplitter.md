# com.openjiuwen.core.retrieval.indexing.processor.chunker.IndexSentenceSplitter

## class IndexSentenceSplitter

```java
public class IndexSentenceSplitter extends TextSplitter
```

SentenceSplitter wrapper with sentence splitting capabilities.

## Constructors

| Signature | Description |
| --- | --- |
| `public IndexSentenceSplitter()` | Create a new `IndexSentenceSplitter` instance. |
| `public IndexSentenceSplitter(Function<String, List<String>> tokenizer, Integer chunkSize, Integer chunkOverlap, java.util.Map<String, Object> splitterConfig, String language)` | Create a new `IndexSentenceSplitter` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<TextChunk> split(Document doc)` | Execute `split`. |
