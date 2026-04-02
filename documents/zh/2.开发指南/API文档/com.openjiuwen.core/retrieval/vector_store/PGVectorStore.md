# com.openjiuwen.core.retrieval.vector_store.PGVectorStore

## 类 PGVectorStore

```java
public class PGVectorStore implements VectorStore
```

`PGVectorStore` 负责在 PostgreSQL/pgvector 中创建表结构、写入记录、执行 dense/sparse/hybrid 检索，以及按 SQL 过滤查询与删除。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PGVectorStore(VectorStoreConfig config)` | 使用默认 `indexType = "hybrid"`，要求配置中能提供连接信息。 |
| `public PGVectorStore(VectorStoreConfig config, String indexType)` | 指定索引类型。 |
| `public PGVectorStore(VectorStoreConfig config, String jdbcUrl, String username, String password, String indexType)` | 使用 JDBC URL 连接数据库。 |
| `public PGVectorStore(VectorStoreConfig config, String jdbcUrl, String username, String password, String indexType, Map<String, Object> options)` | JDBC 连接并允许传入 `vector_field` 等 options。 |
| `public PGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType)` | 使用 `DataSource`。 |
| `public PGVectorStore(VectorStoreConfig config, DataSource dataSource, String indexType, Map<String, Object> options)` | 使用 `DataSource` 并附带 options。 |

## 公开方法

### `public String getCollectionName()` / `public void setCollectionName(String collectionName)` / `public VectorStore withCollection(String collectionName)`

读取、切换或派生表名；新表名必须是合法 SQL 标识符。

### `public void ensureCollection(String collectionName, String indexType, Integer dimension, Map<String, Object> options)`

创建 `vector` 扩展、主表与索引。`PGVectorStoreTest` 验证调用 `add(...)` 时会自动触发建表与建索引。

**异常：**

- 构造阶段没有 `jdbcUrl` 且没有 `dataSource` 时抛出异常。
- `jdbcUrl` 不以 `jdbc:postgresql://` 开头时抛出异常。
- 向量维度超过上限或不合法时抛出异常。

### `public void checkVectorField()`

检查当前表是否具备 `id`、`text`、`vector`、`metadata`、`doc_id`、`chunk_id` 等必需列，并确认向量列类型为 `vector(n)`。

### `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)`

批量 upsert 数据；写入前会自动推断向量维度并调用 `ensureCollection(...)`。

### `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)`

执行 dense 检索，并把 raw distance 归一化到最终分数。`PGVectorStoreTest` 验证余弦 raw score `0.2` 会被映射为最终分数 `0.8`，同时写入 `metadata.raw_score` 与 `metadata.raw_score_scaled`。

### `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)`

使用 `to_tsvector` 与 `websearch_to_tsquery` 执行全文检索。

### `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)`

结合 dense 与 sparse 结果做混合排序。

**说明：**

- `options.rank_config` 为 `RRFRankConfig` 时使用 `FusionUtils.rrfFusionSearch(...)`。
- `options.rank_config` 为 `WeightedRankConfig` 时按 `denseContent` / `denseName` / `sparseContent` 进行加权。
- 缺少 `queryVector` 时回退为纯 sparse；缺少 `queryText` 时回退为纯 dense。

### `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)`

按 chunk id / row id 与 metadata 过滤条件生成 SQL 删除语句。

### `public boolean tableExists(String tableName)` / `public void deleteTable(String tableName)` / `public long count(String tableName)`

分别用于检查表是否存在、删除表以及统计行数。

### `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)`

按字段或 metadata JSON 条件查询记录。

### 配置访问器

`getDatabaseName()`、`getDistanceMetric()`、`getIndexType()`、`getTextField()`、`getVectorField()`、`getSparseVectorField()`、`getMetadataField()`、`getDocIdField()` 返回当前实例的连接与字段配置。
