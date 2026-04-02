# com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore

## class InMemoryVectorStore

```java
public class InMemoryVectorStore extends AbstractRetrievalVectorStoreAdapter
```

Foundation-store in-memory vector store.

## Constructors

| Signature | Description |
| --- | --- |
| `public InMemoryVectorStore()` | Create a new `InMemoryVectorStore` instance. |
| `public InMemoryVectorStore(Map<String, Object> options)` | Create a new `InMemoryVectorStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static VectorStoreConfig config(String storeType, Map<String, Object> options)` | Execute `config`. |
| `static String indexType(Map<String, Object> options)` | Execute `indexType`. |
| `static String stringOption(Map<String, Object> options, String key, String altKey, String fallback)` | Execute `stringOption`. |
