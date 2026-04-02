# com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore

## 类 InMemoryVectorStore

```java
public class InMemoryVectorStore implements VectorStore, SchemaMutableVectorStore
```

`InMemoryVectorStore` 是面向本地回归测试和兼容场景的内存向量库实现，支持向量检索、BM25 稀疏检索、混合检索、过滤查询和简单 schema 变更。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InMemoryVectorStore(String collectionName)` | 使用 `new VectorStoreConfig("chroma", collectionName)` 和 `indexType = "hybrid"` 初始化。 |
| `public InMemoryVectorStore(VectorStoreConfig config, String indexType)` | 使用显式配置与索引类型初始化。 |

## 公开方法

### `public String getCollectionName()` / `public void setCollectionName(String collectionName)` / `public VectorStore withCollection(String collectionName)`

用于读取、切换或派生集合名。`withCollection(...)` 会复用同一个内存后端，但为新集合创建独立视图。`InMemoryVectorStoreTest` 验证不同集合之间互不影响。

### `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)`

把记录写入当前集合。记录 `id` 会按 `id -> chunk_id -> metadata.chunk_id -> UUID` 的顺序推导。

### `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)`

执行向量检索，得分由 `distanceMetric` 决定：`dot`、`euclidean` 或默认余弦相似度。

### `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)`

执行 BM25 风格稀疏检索。`InMemoryVectorStoreTest` 验证词频更高的文档会得到更高分数。

### `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)`

按 `alpha * vector + (1 - alpha) * sparse` 组合归一化向量得分与稀疏得分。

### `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)`

按 id 或过滤条件删除记录；任一删除生效时返回 `true`。

### `public boolean tableExists(String tableName)` / `public void deleteTable(String tableName)` / `public long count(String tableName)`

用于检查、删除与统计内存集合。

### `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)`

按字段或 metadata 过滤返回结果。

### `public List<String> listCollectionNames()`

返回当前后端已知的全部集合名。

### `public Map<String, Object> getCollectionMetadata(String collectionName)` / `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)`

读取或增量更新集合元数据。

### `public void updateSchema(String collectionName, List<?> operations)`

按操作列表遍历记录并修改字段，支持 `AddScalarFieldOperation`、`RenameScalarFieldOperation`、`UpdateScalarFieldTypeOperation` 与 `UpdateEmbeddingDimensionOperation` 这几类通过反射识别的操作名。

### 配置访问器

`getDatabaseName()`、`getDistanceMetric()`、`getIndexType()`、`getTextField()`、`getVectorField()`、`getSparseVectorField()`、`getMetadataField()`、`getDocIdField()` 都直接返回构造阶段确定的固定字段配置。
