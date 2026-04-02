# com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer

## class MilvusIndexer

```java
public class MilvusIndexer implements Indexer
```

Milvus-backed index manager for retrieval.

## Constructors

| Signature | Description |
| --- | --- |
| `public MilvusIndexer(MilvusVectorStore vectorStore)` | Create a new `MilvusIndexer` instance. |
| `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String indexType)` | Create a new `MilvusIndexer` instance. |
| `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)` | Create a new `MilvusIndexer` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | Build the target index from the provided text chunks. |
| `public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | Update index entries for one document. |
| `public boolean deleteIndex(String docId, String indexName, Map<String, Object> options)` | Delete indexed entries for the target document or index. |
| `public boolean indexExists(String indexName)` | Return whether the target index exists. |
| `public Map<String, Object> getIndexInfo(String indexName)` | Return backend metadata for the target index. |
| `public void close()` | Release held resources. |
| `public String getDatabaseName()` | Return the configured database name. |
| `public String getDistanceMetric()` | Execute `getDistanceMetric`. |
| `public String getIndexType()` | Execute `getIndexType`. |
| `public String getTextField()` | Execute `getTextField`. |
| `public String getVectorField()` | Execute `getVectorField`. |
| `public String getSparseVectorField()` | Execute `getSparseVectorField`. |
| `public String getMetadataField()` | Execute `getMetadataField`. |
| `public String getDocIdField()` | Execute `getDocIdField`. |

## Notes

- Related tests: `IndexerFactoryTest.java`, `MilvusIndexerTest.java`.
