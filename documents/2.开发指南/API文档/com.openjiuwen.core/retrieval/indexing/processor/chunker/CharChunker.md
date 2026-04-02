# com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker

## class CharChunker

```java
public class CharChunker extends Chunker
```

Character window chunker.

## Constructors

| Signature | Description |
| --- | --- |
| `public CharChunker(int chunkSize, int chunkOverlap)` | Create a new `CharChunker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> chunkText(String text)` | Execute `chunkText`. |

## Notes

- Related tests: `CharChunkerTest.java`, `KnowledgeBaseTest.java`, `MilvusKnowledgeBaseTest.java`, `PGVectorKnowledgeBaseTest.java`.
