# com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore

## class MilvusVectorStore

```java
public class MilvusVectorStore implements VectorStore
```

Milvus-backed vector store for retrieval.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `client` | `final MilvusClientV2` | Milvus client used for schema, insert, search, query, and delete operations. |
| `ownsClient` | `final boolean` | Whether this instance should close the client on `close()`. |
| `loadedCollections` | `final Set<String>` | Cache of collections already loaded into Milvus query nodes. |
| `knownCollections` | `final Set<String>` | Cache of collections already known to exist. |
| `databaseName` | `final String` | Optional Milvus database name. |
| `distanceMetric` | `final String` | Dense search distance metric. |
| `indexType` | `final String` | Retrieval mode advertised by the store. |
| `milvusUri` | `final String` | Connection URI used when this instance owns the client. |
| `milvusToken` | `final String` | Optional Milvus auth token. |
| `textField` | `final String` | Text field used for BM25 and payload reads. |
| `vectorField` | `final String` | Dense vector field name. |
| `sparseVectorField` | `final String` | Sparse vector field name. |
| `metadataField` | `final String` | JSON metadata field name. |
| `docIdField` | `final String` | Logical document-id field name. |
| `collectionName` | `String` | Active collection name. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri)` | Create a Milvus-backed store with the default retrieval index type. |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String indexType)` | Create a Milvus-backed store with an explicit index type. |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)` | Create a Milvus-backed store with explicit auth token and index type. |
| `public MilvusVectorStore(MilvusClientV2 client, VectorStoreConfig config, String indexType)` | Wrap an already constructed Milvus client without taking ownership of its lifecycle. |

## Methods

| Signature | Description |
| --- | --- |
| `public static MilvusClientV2 createClient(String databaseName, String milvusUri, String milvusToken)` | Create a Milvus client, create the database when needed, and switch the client to that database. |
| `public MilvusClientV2 getClient()` | Return the underlying Milvus client. |
| `public String getMilvusUri()` | Return the configured Milvus URI. |
| `public String getMilvusToken()` | Return the configured Milvus auth token. |
| `public String getCollectionName()` | Return the active collection name. |
| `public void setCollectionName(String collectionName)` | Switch the active collection name. |
| `public VectorStore withCollection(String collectionName)` | Return a scoped store that shares the client and caches under another collection name. |
| `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | Bootstrap the target collection when necessary, insert data in batches, and flush the collection afterward. |
| `public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension)` | Convenience overload that creates the target collection with empty options. |
| `public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension, Map<String, Object> options)` | Create a Milvus collection schema for dense, sparse, or hybrid retrieval and configure the required indexes. |
| `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run dense vector search with optional metadata filters. |
| `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run BM25 search over the sparse vector field with optional metadata filters. |
| `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | Run native Milvus hybrid search when available and fall back to weighted fusion of dense and sparse searches when native hybrid fails. |
| `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | Delete rows by `chunk_id` and-or additional filter expressions, then flush the collection. |
| `public boolean tableExists(String tableName)` | Return whether the named collection exists, using the local cache to avoid repeated lookups. |
| `public void deleteTable(String tableName)` | Drop the named collection and clear its local cache entries. |
| `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | Query rows by filters without vector scoring. |
| `public long count(String tableName)` | Return the collection entity count from Milvus collection statistics. |
| `public void close()` | Close the owned Milvus client. Shared clients are left open. |
| `public String getDatabaseName()` | Return the configured database name. |
| `public String getDistanceMetric()` | Return the configured dense metric. |
| `public String getIndexType()` | Return the configured index type. |
| `public String getTextField()` | Return the configured text field name. |
| `public String getVectorField()` | Return the configured dense vector field name. |
| `public String getSparseVectorField()` | Return the configured sparse vector field name. |
| `public String getMetadataField()` | Return the configured metadata field name. |
| `public String getDocIdField()` | Return the configured document-id field name. |

## Notes

- `MilvusVectorStoreTest.java` verifies dense-score normalization, hybrid fallback behavior, filter-expression generation for `queryByFilters(...)`, and delete-time flush semantics.
