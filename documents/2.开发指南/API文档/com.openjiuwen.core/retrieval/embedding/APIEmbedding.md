# com.openjiuwen.core.retrieval.embedding.APIEmbedding

## class APIEmbedding

```java
public class APIEmbedding implements Embedding, AutoCloseable
```

Universal HTTP embedding client aligned with the Python APIEmbedding implementation.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `config` | `final EmbeddingConfig` | config. |
| `modelName` | `final String` | model name. |
| `apiKey` | `final String` | api key. |
| `apiUrl` | `final String` | api url. |
| `timeout` | `final int` | timeout. |
| `maxRetries` | `final int` | max retries. |
| `maxBatchSize` | `final int` | max batch size. |
| `maxConcurrent` | `final int` | max concurrent. |
| `headers` | `final Map<String, String>` | headers. |
| `httpClient` | `final HttpClient` | http client. |
| `executor` | `final ExecutorService` | executor. |
| `dimension` | `volatile Integer` | dimension. |

## Constructors

| Signature | Description |
| --- | --- |
| `public APIEmbedding(EmbeddingConfig config)` | Create a new `APIEmbedding` instance. |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent)` | Create a new `APIEmbedding` instance. |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, HttpClient httpClient)` | Create a new `APIEmbedding` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Float> embedQuery(String text, Map<String, Object> options)` | Generate embeddings for query. |
| `List<List<Float>> embeddings = getEmbeddings(text, options)` | Return the embeddings. |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | Generate embeddings for documents. |
| `BaseCallback callback = resolveCallback(options, indices)` | Execute `resolveCallback`. |
| `synchronized (this)` | Execute `synchronized`. |
| `protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options)` | Return the embeddings. |
| `HttpResponse<String> response = httpClient.send( requestBuilder.build(), HttpResponse.BodyHandlers.ofString())` | Execute `send`. |
| `List<Float> embedding = new ArrayList<>(item.size())` | Execute `size`. |
| `List<String> nonEmpty = new ArrayList<>(texts.size())` | Execute `size`. |
| `Object callbackClass = options == null ? null : options.get("callback_cls")` | Execute `get`. |

## Notes

- Related tests: `APIEmbeddingTest.java`.
