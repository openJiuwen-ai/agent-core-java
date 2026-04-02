# com.openjiuwen.core.retrieval.embedding.VLLMEmbedding

## class VLLMEmbedding

```java
public class VLLMEmbedding extends OpenAIEmbedding
```

vLLM-compatible multimodal embedding client.

## Constructors

| Signature | Description |
| --- | --- |
| `public VLLMEmbedding(EmbeddingConfig config)` | Create a new `VLLMEmbedding` instance. |
| `public VLLMEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient)` | Create a new `VLLMEmbedding` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> parseMultimodalInput(MultimodalDocument document, Map<String, Object> options)` | Parse multimodal input. |
| `public List<Float> embedMultimodal(Object input, Map<String, Object> options)` | Generate embeddings for multimodal. |
| `Map<String, Object> kwargs = parseMultimodalInput(document, options)` | Parse multimodal input. |
| `public List<Float> embedMultimodalSync(MultimodalDocument document)` | Generate embeddings for multimodal sync. |
| `public List<Float> embedMultimodalSync(Object input, Map<String, Object> options)` | Generate embeddings for multimodal sync. |
| `Map<String, Object> kwargs = parseMultimodalInput(document, options == null ? new LinkedHashMap<>() : options)` | Parse multimodal input. |

## Notes

- Related tests: `VLLMEmbeddingTest.java`.
