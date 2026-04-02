# com.openjiuwen.core.foundation.store.base_embedding.Embedding

## class Embedding

```java
public abstract class Embedding
```

Embedding model abstract base class.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract List<Float> embedQuery(String text)` | Embed query text into a vector. |
| `public abstract List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | Embed document texts into vectors. |
| `public List<List<Float>> embedDocuments(List<String> texts)` | Embed document texts into vectors with default batch size. |
| `public abstract int getDimension()` | Return the embedding dimension. |
