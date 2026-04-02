# com.openjiuwen.core.memory.manage.mem_model.SemanticStore

## class SemanticStore

```java
public class SemanticStore
```

Semantic store wrapping VectorStore for memory module.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `VECTOR_FIELD` | `String` | vector field. |
| `TEXT_FIELD` | `String` | text field. |
| `ID_FIELD` | `String` | id field. |
| `vectorStore` | `VectorStore` | vector store. |
| `embeddingModel` | `Embedding` | embedding model. |
| `knownCollections` | `Set<String>` | known collections. |
| `collectionMetadata` | `Map<String, Map<String, Object>>` | collection metadata. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SemanticStore(VectorStore vectorStore)` | Create a new `SemanticStore` instance. |
| `public SemanticStore(VectorStore vectorStore, Embedding embedding)` | Create a new `SemanticStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void initializeEmbeddingModel(Embedding embeddingModel)` | Execute `initializeEmbeddingModel`. |
| `public boolean collectionExist(String collectionName)` | Check if a collection exists. |
| `public void createCollection(String collectionName, int dimension, Map<String, Object> schema)` | Create a collection when the backend supports explicit bootstrap. |
| `public boolean addDocs(List<Map.Entry<String, String>> docs, String tableName)` | Add documents as (id, text) pairs. |
| `public List<Map.Entry<String, Double>> search(String query, String tableName, int topK)` | Search by text query. |
| `public void deleteDocs(List<String> ids, String tableName)` | Delete documents by IDs from a collection. |
| `public void deleteTable(String tableName)` | Delete an entire collection/table. |
| `public List<String> listCollectionNames()` | List collection names. |
| `public boolean updateSchema(String collectionName, List<?> operations)` | Update schema. |
| `public Map<String, Object> getCollectionMetadata(String collectionName)` | Get collection metadata. |
| `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | Update collection metadata. |

## Notes

- Related tests: `SemanticStoreMilvusTest.java`, `SemanticStorePGVectorTest.java`
