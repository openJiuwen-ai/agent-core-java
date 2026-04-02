# com.openjiuwen.core.retrieval.indexing.processor.chunker.TokenizerChunker

## class TokenizerChunker

```java
public class TokenizerChunker extends Chunker
```

Token-aware chunker backed by {@link SentenceSplitter}.

## Constructors

| Signature | Description |
| --- | --- |
| `public TokenizerChunker(int chunkSize, int chunkOverlap)` | Create a new `TokenizerChunker` instance. |
| `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer)` | Create a new `TokenizerChunker` instance. |
| `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language, Map<String, Object> splitterConfig)` | Create a new `TokenizerChunker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> chunkText(String text)` | Chunk the input text into smaller segments. |
| `public Function<String, List<String>> getTokenizer()` | Execute `getTokenizer`. |
| `public String getLanguage()` | Execute `getLanguage`. |
| `public Map<String, Object> getSplitterConfig()` | Execute `getSplitterConfig`. |

## Notes

- Related tests: `TokenizerChunkerTest.java`.
