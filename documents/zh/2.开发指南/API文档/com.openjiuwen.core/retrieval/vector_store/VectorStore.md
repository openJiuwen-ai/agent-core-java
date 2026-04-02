# com.openjiuwen.core.retrieval.vector_store.VectorStore

## 接口 VectorStore

```java
public interface VectorStore extends IndexBackendConfig, AutoCloseable
```

`VectorStore` 是 retrieval 子系统统一的向量库抽象，约定 collection 管理、写入、检索、删除与统计接口。

## 抽象方法

### collection 管理

- `String getCollectionName()`：返回当前 collection 名称。
- `void setCollectionName(String collectionName)`：切换当前 collection。
- `VectorStore withCollection(String collectionName)`：返回指向其他 collection 的同类实例。

### 写入与检索

- `void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)`：写入数据。
- `List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)`：执行 dense 检索。
- `List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)`：执行 sparse 检索。
- `List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)`：执行混合检索。

### 删除与查询

- `boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)`：按 id 或过滤条件删除。
- `boolean tableExists(String tableName)`：检查 collection / table 是否存在。
- `void deleteTable(String tableName)`：删除 collection / table。
- `List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)`：按过滤条件查询。
- `long count(String tableName)`：统计记录数。

## 默认方法

### `default void checkVectorField()`

默认空实现；具体后端可覆写做 schema 校验。

### `default void ensureCollection(String collectionName, String indexType, Integer dimension)`

默认委托给四参数版本并传入 `Map.of()`。

### `default void ensureCollection(String collectionName, String indexType, Integer dimension, Map<String, Object> options)`

默认空实现；具体后端可覆写执行建表建 collection。

### `default void close()`

默认空实现。
