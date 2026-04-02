# com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker

## class TextChunker

```java
public class TextChunker extends Chunker
```

Composite chunker with preprocessing.

## Constructors

| Signature | Description |
| --- | --- |
| `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit)` | Create a new `TextChunker` instance. |
| `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit, Function<String, List<String>> tokenizer, String language)` | Create a new `TextChunker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> chunkText(String text)` | Chunk the input text into smaller segments. |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | Execute `chunkDocuments`. |
