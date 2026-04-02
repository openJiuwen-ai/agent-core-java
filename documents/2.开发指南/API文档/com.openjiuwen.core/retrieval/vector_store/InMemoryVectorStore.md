# com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore

## class InMemoryVectorStore

```java
public class InMemoryVectorStore implements VectorStore, SchemaMutableVectorStore
```

Local in-memory vector store used for translated retrieval regression tests.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `BM25_K1` | `static final double` | `1.5` | BM25 saturation parameter used by sparse scoring. |
| `BM25_B` | `static final double` | `0.75` | BM25 length-normalization parameter used by sparse scoring. |
| `backend` | `final Backend` | `-` | Shared in-memory backend that stores collections and collection metadata. |
| `databaseName` | `final String` | `-` | Logical in-memory database name. |
| `collectionName` | `String` | `-` | Active collection name. |
| `distanceMetric` | `final String` | `-` | Dense vector distance metric. |
| `indexType` | `final String` | `-` | Retrieval mode advertised by the store. |
| `textField` | `final String` | `-` | Source field used for sparse search text. |
| `vectorField` | `final String` | `-` | Source field used for dense vectors. |
| `sparseVectorField` | `final String` | `-` | Placeholder sparse-vector field name kept for schema parity with remote stores. |
| `metadataField` | `final String` | `-` | Source field used for metadata maps. |
| `docIdField` | `final String` | `-` | Metadata field used as the logical document id. |

## Constructors

| Signature | Description |
| --- | --- |
| `public InMemoryVectorStore(String collectionName)` | Create an in-memory store for the supplied collection using default config values. |
| `public InMemoryVectorStore(VectorStoreConfig config, String indexType)` | Create an in-memory store from explicit vector-store configuration and index type. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getCollectionName()` | Return the active collection name. |
| `public void setCollectionName(String collectionName)` | Switch the active collection and create the collection map if needed. |
| `public VectorStore withCollection(String collectionName)` | Return a scoped view that shares the same in-memory backend under another collection name. |
| `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | Upsert records into the current collection, choosing an id from `id`, `chunk_id`, `metadata.chunk_id`, or a generated UUID. |
| `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run dense similarity search against stored vectors. |
| `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run BM25-like sparse ranking over the filtered corpus. |
| `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | Combine normalized dense and sparse scores with `alpha` weighting. |
| `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | Remove records by explicit ids and-or filter expressions. |
| `public boolean tableExists(String tableName)` | Return whether the named collection exists in the current in-memory backend. |
| `public void deleteTable(String tableName)` | Delete a collection and its stored metadata. |
| `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | Return records that match filters without vector scoring. |
| `public long count(String tableName)` | Count rows in the named collection. |
| `public List<String> listCollectionNames()` | List all collection names in the current in-memory backend. |
| `public Map<String, Object> getCollectionMetadata(String collectionName)` | Return a copy of the metadata map for the named collection. |
| `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | Merge collection-level metadata into the backend metadata store. |
| `public void updateSchema(String collectionName, List<?> operations)` | Apply schema-migration operations to every stored record in the named collection. |
| `public String getDatabaseName()` | Return the logical database name. |
| `public String getDistanceMetric()` | Return the configured dense distance metric. |
| `public String getIndexType()` | Return the configured index type. |
| `public String getTextField()` | Return the configured text field name. |
| `public String getVectorField()` | Return the configured dense vector field name. |
| `public String getSparseVectorField()` | Return the configured sparse vector field name. |
| `public String getMetadataField()` | Return the configured metadata field name. |
| `public String getDocIdField()` | Return the configured document-id field name. |

## Notes

- `InMemoryVectorStoreTest.java` covers BM25 term-frequency ranking, collection isolation through `withCollection(...)`, dense sorting, hybrid score fusion, delete semantics, and filter-only queries.
