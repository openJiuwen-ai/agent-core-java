# com.openjiuwen.core.retrieval.vector_store.PGVectorStore

## class PGVectorStore

```java
public class PGVectorStore implements VectorStore
```

PostgreSQL/pgvector-backed vector store for retrieval.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `PUBLIC_SCHEMA` | `static final String` | `"public"` | Schema prefix used for generated tables and indexes. |
| `DEFAULT_BATCH_SIZE` | `static final int` | `128` | Default batch size for upserts. |
| `MAX_VECTOR_DIMENSION` | `static final int` | `2000` | Upper bound accepted when bootstrapping vector columns. |
| `dataSource` | `final DataSource` | `-` | Optional externally supplied JDBC data source. |
| `jdbcUrl` | `final String` | `-` | JDBC URL used when no data source is supplied. |
| `username` | `final String` | `-` | Optional JDBC username. |
| `password` | `final String` | `-` | Optional JDBC password. |
| `databaseName` | `final String` | `-` | Expected PostgreSQL database name. |
| `distanceMetric` | `final String` | `-` | Dense vector distance metric. |
| `indexType` | `final String` | `-` | Retrieval mode advertised by the store. |
| `textField` | `final String` | `-` | Text column used for lexical search. |
| `vectorField` | `final String` | `-` | Dense vector column name. |
| `docIdField` | `final String` | `-` | Logical document-id column name. |
| `chunkIdField` | `final String` | `-` | Logical chunk-id column name. |
| `metadataField` | `final String` | `-` | JSONB metadata column name. |
| `sparseVectorField` | `final String` | `-` | Reserved sparse-vector field name retained for config parity. |
| `collectionName` | `String` | `-` | Active table name. |

## Constructors

| Signature | Description |
| --- | --- |
| `public PGVectorStore(VectorStoreConfig config)` | Create a PGVector-backed store with the default index type. |
| `public PGVectorStore(VectorStoreConfig config, String indexType)` | Create a PGVector-backed store with an explicit index type. |
| `public PGVectorStore(VectorStoreConfig config, String jdbcUrl, String username, String password, String indexType)` | Create a PGVector-backed store from JDBC credentials. |
| `public PGVectorStore(VectorStoreConfig config, String jdbcUrl, String username, String password, String indexType, Map<String, Object> options)` | Create a PGVector-backed store from JDBC credentials plus extra options such as `vector_field`. |
| `public PGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType)` | Create a PGVector-backed store from an existing `DataSource`. |
| `public PGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType, Map<String, Object> options)` | Create a PGVector-backed store from an existing `DataSource` plus extra options. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getCollectionName()` | Return the active table name. |
| `public void setCollectionName(String collectionName)` | Switch the active table name after identifier validation. |
| `public VectorStore withCollection(String collectionName)` | Return a scoped view for another table while preserving the current connection strategy. |
| `public void ensureCollection(String collectionName, String indexType, Integer dimension, Map<String, Object> options)` | Create the `vector` extension, target table, secondary indexes, and ANN index for the requested collection. |
| `public void checkVectorField()` | Validate that the target table exposes the required columns and that the configured vector field uses `vector(n)`. |
| `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | Bootstrap the table when needed and batch-upsert normalized rows. |
| `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run dense pgvector search and normalize the returned score into the `SearchResult` metadata. |
| `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | Run PostgreSQL full-text search with `websearch_to_tsquery`. |
| `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | Combine dense and sparse results with weighted fusion by default, or use `RRFRankConfig` / `WeightedRankConfig` when supplied. |
| `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | Delete rows by ids, chunk ids, and-or metadata-aware filters inside one SQL transaction. |
| `public boolean tableExists(String tableName)` | Return whether the target table exists. |
| `public void deleteTable(String tableName)` | Drop the target table inside one SQL transaction. |
| `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | Return rows matching filters without similarity scoring. |
| `public long count(String tableName)` | Return the row count for the target table. |
| `public String getDatabaseName()` | Return the configured database name. |
| `public String getDistanceMetric()` | Return the configured dense distance metric. |
| `public String getIndexType()` | Return the configured index type. |
| `public String getTextField()` | Return the configured text field name. |
| `public String getVectorField()` | Return the configured vector field name. |
| `public String getSparseVectorField()` | Return the configured sparse vector field name. |
| `public String getMetadataField()` | Return the configured metadata field name. |
| `public String getDocIdField()` | Return the configured document-id field name. |

## Notes

- `PGVectorStoreTest.java` covers constructor validation, table bootstrapping, dense-score normalization, SQL-backed delete and count paths, schema validation, and both weighted and RRF hybrid fusion.
