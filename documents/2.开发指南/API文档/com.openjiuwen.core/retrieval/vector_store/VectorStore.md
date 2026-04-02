# com.openjiuwen.core.retrieval.vector_store.VectorStore

## interface VectorStore

```java
public interface VectorStore extends IndexBackendConfig, AutoCloseable
```

Unified vector-store abstraction shared by dense, sparse, and hybrid retrieval backends.

## Methods

| Signature | Description |
| --- | --- |
| `String getCollectionName()` | Return the active collection or table name. |
| `void setCollectionName(String collectionName)` | Switch the active collection or table name. |
| `VectorStore withCollection(String collectionName)` | Return a scoped view of the same backend for another collection. |
| `default void checkVectorField()` | Validate that the configured vector field matches the backend schema. The default implementation is a no-op. |
| `default void ensureCollection(String collectionName, String indexType, Integer dimension)` | Convenience overload that creates the target collection with empty options. |
| `default void ensureCollection(String collectionName, String indexType, Integer dimension, Map<String, Object> options)` | Create the collection or table and any required indexes before writes. The default implementation is a no-op. |
| `void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | Insert or upsert rows into the active collection. |
| `List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run dense vector search. |
| `List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run sparse / lexical search. |
| `List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | Combine dense and sparse retrieval into one ranked result set. |
| `boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | Delete rows by explicit ids and-or filter expressions. |
| `boolean tableExists(String tableName)` | Return whether the target collection or table exists. |
| `void deleteTable(String tableName)` | Drop the target collection or table. |
| `List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | Return rows that match filter expressions without similarity scoring. |
| `long count(String tableName)` | Count rows in the target collection or table. |
| `default void close()` | Release backend resources. The default implementation is a no-op. |

## Notes

- The retrieval tests cover dense, sparse, hybrid, filter, delete, and factory-backed creation paths across the in-memory, Milvus, and PGVector implementations.
