# com.openjiuwen.core.retrieval.indexing.indexer.Indexer

## interface Indexer

```java
public interface Indexer extends IndexBackendConfig, AutoCloseable
```

Index manager abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | Execute `buildIndex`. |
| `boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | Execute `updateIndex`. |
| `boolean deleteIndex(String docId, String indexName, Map<String, Object> options)` | Delete index. |
| `boolean indexExists(String indexName)` | Execute `indexExists`. |
| `Map<String, Object> getIndexInfo(String indexName)` | Return the index info. |
| `default void close()` | Close held resources. |

## Notes

- Related tests: `InMemoryIndexerTest.java`, `IndexerFactoryTest.java`, `KnowledgeBaseTest.java`, `MilvusIndexerTest.java`.
