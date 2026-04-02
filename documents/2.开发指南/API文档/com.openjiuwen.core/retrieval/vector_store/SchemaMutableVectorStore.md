# com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore

## interface SchemaMutableVectorStore

```java
public interface SchemaMutableVectorStore extends VectorStore
```

Optional extension for vector stores that support schema and collection metadata updates.

## Methods

| Signature | Description |
| --- | --- |
| `List<String> listCollectionNames()` | Execute `listCollectionNames`. |
| `Map<String, Object> getCollectionMetadata(String collectionName)` | Return the collection metadata. |
| `void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | Execute `updateCollectionMetadata`. |
| `void updateSchema(String collectionName, List<?> operations)` | Execute `updateSchema`. |
