# com.openjiuwen.core.foundation.store.vector.MilvusVectorStore

## 类 MilvusVectorStore

```java
public class MilvusVectorStore implements VectorStore
```

`MilvusVectorStore` 封装 `MilvusClientV2`，负责 collection 创建、写入、dense/sparse/hybrid 检索、条件查询与删除。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri)` | 使用默认 `indexType = "hybrid"`。 |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String indexType)` | 指定索引类型。 |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)` | 指定 URI、token 与索引类型。 |
| `public MilvusVectorStore(MilvusClientV2 client, VectorStoreConfig config, String indexType)` | 复用外部提供的 `MilvusClientV2`。 |

## 公开静态方法

### `public static MilvusClientV2 createClient(String databaseName, String milvusUri, String milvusToken)`

创建并按需切换数据库；当目标数据库不存在时会先调用 `createDatabase(...)`。

## 公开方法

### 连接与配置访问

- `getClient()`：返回内部 `MilvusClientV2`。
- `getMilvusUri()` / `getMilvusToken()`：返回构造时保存的连接参数。
- `getCollectionName()` / `setCollectionName(String collectionName)` / `withCollection(String collectionName)`：读取、切换或派生 collection。
- `getDatabaseName()`、`getDistanceMetric()`、`getIndexType()`、`getTextField()`、`getVectorField()`、`getSparseVectorField()`、`getMetadataField()`、`getDocIdField()`：返回后端字段与配置。

### `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)`

批量写入记录；写入前会通过 `ensureCollectionForWrite(...)` 自动推断维度并建 collection，写入完成后执行 `flush(...)`。

### `public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension)`

等价于调用四参数版本并传入空 options。

### `public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension, Map<String, Object> options)`

为目标 collection 建立 schema、BM25 函数、稠密/稀疏索引与 metadata 字段。

**说明：**

- `indexType = vector` 时仅建 dense 字段。
- `indexType = bm25` 时仅建 sparse 字段。
- 其他索引类型会同时建立 dense 与 sparse 字段。

### `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)`

执行 dense 向量搜索，并把 Milvus 原始分数归一化到 `SearchResult.score`。`MilvusVectorStoreTest` 验证余弦 raw score `0.8` 会映射为 `0.9`，同时写入 `metadata.raw_score` 与 `metadata.raw_score_scaled`。

### `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)`

基于 `EmbeddedText` 与 BM25 稀疏向量字段执行搜索。

### `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)`

优先调用 Milvus 原生 hybrid search；若运行时失败，则回退到 dense + sparse 的本地融合。

**说明：**

- `options.rank_config` 为 `RRFRankConfig` 或 `WeightedRankConfig` 时，会切换对应 ranker。
- `MilvusVectorStoreTest` 验证原生 hybrid 失败时仍会返回加权融合结果。

### `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)`

把 `ids` 与过滤条件渲染为 Milvus filter 表达式并执行删除；删除后触发 `flush(...)`。

### `public boolean tableExists(String tableName)` / `public void deleteTable(String tableName)` / `public long count(String tableName)`

分别用于检查 collection 是否存在、删除 collection 与读取实体数。

### `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)`

按 filter 表达式查询现有记录。测试验证列表值会渲染为 `field in [..]`。

### `public void close()`

仅当实例自己创建了 `MilvusClientV2` 时才关闭客户端；外部注入 client 的场景不会代为关闭。
