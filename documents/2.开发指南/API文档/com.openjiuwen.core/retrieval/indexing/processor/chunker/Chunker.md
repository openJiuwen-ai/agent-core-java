# com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker

## class Chunker

```java
public abstract class Chunker implements Processor<List<Document>, List<TextChunk>>
```

Chunker abstraction for documents.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract List<String> chunkText(String text)` | Chunk the input text into smaller segments. |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | Execute `chunkDocuments`. |
| `public List<TextChunk> process(List<Document> input, Map<String, Object> options)` | Process the input values and return transformed results. |

## Notes

- Related tests: `CharChunkerTest.java`, `TokenizerChunkerTest.java`.
