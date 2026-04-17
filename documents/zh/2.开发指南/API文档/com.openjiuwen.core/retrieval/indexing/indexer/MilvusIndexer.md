# com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer

## 类 MilvusIndexer

```java
public class MilvusIndexer implements Indexer
```

`MilvusIndexer` 是面向 Milvus 的索引器实现，除普通写入外，还负责 collection 创建、维度探测和重复 `doc_id` 校验。

## 构造方法

### `public MilvusIndexer(MilvusVectorStore vectorStore)`

复用外部已创建的 `MilvusVectorStore`，关闭索引器时不会主动关闭该 store。

### `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String indexType)`

内部创建 `MilvusVectorStore`，并把 `ownsStore` 设为 `true`。

### `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)`

与上一个构造类似，但额外传入 token。

## 公开方法

### `public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)`

- 空 chunk 列表直接返回 `true`。
- 非空时会先调用 `ensureCollection(...)`。
- 插入前会按 `doc_id` 查询现有记录；若检测到重复 `doc_id`，抛出 `RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR`。

### `public boolean updateIndex(...)`

先删除，再复用 `buildIndex(...)`。

### `public boolean deleteIndex(String docId, String indexName, Map<String, Object> options)`

通过 `VectorStore.delete(...)` 按 `doc_id` 过滤删除。

### `public Map<String, Object> getIndexInfo(String indexName)`

返回 `exists`、`index_name`、`count`，以及在 collection 存在时补充 `field_names`、`vector_fields`。

### `public void close()`

仅当索引器自己创建了 `MilvusVectorStore` 时才关闭底层 store。

## 相关测试

- `MilvusIndexerTest` 验证 collection 创建请求会包含 `vector` 与 `sparse_vector` 字段。
- 同一测试还验证写入文档时包含 `chunk_id`、`doc_id`、`text`，且不会写入 `id` 字段。
- 删除逻辑测试验证 Milvus 过滤表达式形如 `doc_id == "doc-1"`。
