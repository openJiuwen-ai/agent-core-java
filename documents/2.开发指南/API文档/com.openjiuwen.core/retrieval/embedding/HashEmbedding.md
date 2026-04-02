# com.openjiuwen.core.retrieval.embedding.HashEmbedding

## class HashEmbedding

```java
public class HashEmbedding implements Embedding
```

Deterministic local embedding based on SHA-256 hashing.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `dimension` | `final int` | dimension. |
| `maxBatchSize` | `final int` | max batch size. |

## Constructors

| Signature | Description |
| --- | --- |
| `public HashEmbedding()` | Create a new `HashEmbedding` instance. |
| `public HashEmbedding(int dimension, int maxBatchSize)` | Create a new `HashEmbedding` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Float> embedQuery(String text)` | Generate embeddings for query. |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | Generate embeddings for documents. |
| `public int getMaxBatchSize()` | Return the max batch size. |

## Notes

- Related tests: `HashEmbeddingTest.java`, `KnowledgeBaseTest.java`.
