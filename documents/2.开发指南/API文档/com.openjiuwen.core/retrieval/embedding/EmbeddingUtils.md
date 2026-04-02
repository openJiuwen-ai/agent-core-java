# com.openjiuwen.core.retrieval.embedding.EmbeddingUtils

## class EmbeddingUtils

```java
public final class EmbeddingUtils
```

Helpers for embedding model implementations.

## Constructors

| Signature | Description |
| --- | --- |
| `private EmbeddingUtils()` | Create a new `EmbeddingUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static List<Float> parseBase64Embedding(String base64Embedding)` | Parse base64 embedding. |
| `byte[] bytes = Base64.getDecoder().decode(base64Embedding)` | Return the decoder. |

## Notes

- Related tests: `EmbeddingUtilsTest.java`.
