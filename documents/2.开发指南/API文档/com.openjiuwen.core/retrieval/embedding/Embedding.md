# com.openjiuwen.core.retrieval.embedding.Embedding

## interface Embedding

```java
public interface Embedding
```

Embedding model abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `List<Float> embedQuery(String text)` | Generate embeddings for query. |
| `default List<Float> embedQuery(String text, Map<String, Object> options)` | Generate embeddings for query. |
| `List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | Generate embeddings for documents. |
| `default List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | Generate embeddings for documents. |
| `int getDimension()` | Return the dimension. |
| `default int getMaxBatchSize()` | Return the max batch size. |

## Notes

- Related tests: `APIEmbeddingTest.java`, `EmbeddingUtilsTest.java`, `HashEmbeddingTest.java`, `InMemoryIndexerTest.java`, `MilvusIndexerTest.java`, `MilvusKnowledgeBaseTest.java`.
