# com.openjiuwen.core.retrieval.indexing.indexer.InMemoryIndexer

## 类 InMemoryIndexer

```java
public class InMemoryIndexer implements Indexer
```

`InMemoryIndexer` 是通用索引器实现，核心思路是把 `TextChunk` 转成文档结构后交给 `VectorStore` 写入。它既可用于真正的内存向量库，也可用于任何遵循 `VectorStore` 接口的后端。

## 构造方法

### `public InMemoryIndexer(VectorStore vectorStore)`

保存底层 `VectorStore`，后续所有 collection、字段名和统计信息都从该对象读取。

## 公开方法

### `public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)`

- 先调用 `vectorStore.withCollection(config.getIndexName())` 切换到目标 collection。
- 当 `config.getIndexType()` 不是 `bm25` 时，会要求 `embedModel` 非空，并按批次调用 `embedDocuments(...)`。
- 最终以固定批大小 `128` 调用 `VectorStore.add(...)`。

### `public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)`

先按 `doc_id` 删除旧记录，再重新调用 `add(...)` 写入新 chunk。

### `public boolean deleteIndex(String docId, String indexName, Map<String, Object> options)`

按 `getDocIdField()` 构造过滤条件删除指定文档。

### `public boolean indexExists(String indexName)`

委托 `vectorStore.tableExists(indexName)`。

### `public Map<String, Object> getIndexInfo(String indexName)`

返回 `index_name`、`count`、`exists` 三个键。

## 字段名透传

- `getDatabaseName()`、`getDistanceMetric()`、`getIndexType()`、`getTextField()`、`getVectorField()`、`getSparseVectorField()`、`getMetadataField()`、`getDocIdField()` 全部直接透传到底层 `VectorStore`。

## 相关测试

- `InMemoryIndexerTest` 验证带 `callback` 的嵌入批处理会在每批完成后调用 `BaseCallback.onBatch(...)`。
