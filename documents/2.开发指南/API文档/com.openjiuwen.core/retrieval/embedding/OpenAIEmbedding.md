# com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding

## class OpenAIEmbedding

```java
public class OpenAIEmbedding extends APIEmbedding
```

OpenAI-compatible embedding client with base64 embedding support.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `configuredDimension` | `final Integer` | configured dimension. |

## Constructors

| Signature | Description |
| --- | --- |
| `public OpenAIEmbedding(EmbeddingConfig config)` | Create a new `OpenAIEmbedding` instance. |
| `public OpenAIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient)` | Create a new `OpenAIEmbedding` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public int getDimension()` | Return the dimension. |
| `public List<Float> embedQuery(String text, Map<String, Object> options)` | Generate embeddings for query. |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | Generate embeddings for documents. |
| `protected List<List<Float>> parseEmbeddings(JsonNode root)` | Parse embeddings. |

## Notes

- Related tests: `OpenAIEmbeddingTest.java`.
