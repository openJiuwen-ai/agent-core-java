# com.openjiuwen.core.retrieval.indexing.indexer.Indexer

## 接口 Indexer

```java
public interface Indexer extends IndexBackendConfig, AutoCloseable
```

`Indexer` 是 retrieval 索引管理的统一入口，负责构建、更新、删除和查询索引状态。

## 抽象方法

- `boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)`：构建新索引。
- `boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)`：更新指定文档对应的索引内容。
- `boolean deleteIndex(String docId, String indexName, Map<String, Object> options)`：删除索引中的某个文档。
- `boolean indexExists(String indexName)`：检查索引是否存在。
- `Map<String, Object> getIndexInfo(String indexName)`：返回索引元信息。

## 默认方法

### `default void close()`

默认空实现。具体实现若持有外部资源，可以自行覆写。
