# com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory

## class VectorStoreFactory

```java
public final class VectorStoreFactory
```

Factory for creating vector stores from configuration.

## Constructors

| Signature | Description |
| --- | --- |
| `private VectorStoreFactory()` | Utility class constructor. Not intended for direct use. |

## Methods

| Signature | Description |
| --- | --- |
| `public static VectorStore createVectorStore(VectorStoreConfig config)` | Create a vector store with empty options. |
| `public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> options)` | Validate the config, resolve the retrieval index type from options, and instantiate `ChromaVectorStore`, `MilvusVectorStore`, or `PGVectorStore`. |

## Notes

- `VectorStoreFactoryTest.java` verifies compatibility-store creation, Milvus client and URI requirements, and PGVector JDBC / `DataSource` option handling.
