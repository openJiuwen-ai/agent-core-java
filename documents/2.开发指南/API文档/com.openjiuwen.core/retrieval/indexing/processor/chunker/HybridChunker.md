# com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker

## class HybridChunker

```java
public class HybridChunker extends Chunker
```

Chunker that skips splitting for specific document types.

## Constructors

| Signature | Description |
| --- | --- |
| `public HybridChunker(Chunker innerChunker)` | Create a new `HybridChunker` instance. |
| `public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen)` | Create a new `HybridChunker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> chunkText(String text)` | Chunk the input text into smaller segments. |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | Execute `chunkDocuments`. |

## Notes

- Related tests: `TokenizerChunkerTest.java`.
