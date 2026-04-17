# com.openjiuwen.core.foundation.store.graph.GraphStore

## interface GraphStore

```java
public interface GraphStore
```

图存储统一接口。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public GraphConfig getConfig()` | 返回当前图存储配置。 |
| `public ExecutorService getEmbedExecutor()` | 返回嵌入任务执行器。 |
| `public Embedding getEmbedder()` | 返回当前嵌入器。 |
| `public static GraphStore fromConfig(GraphConfig config)` | 约定式静态创建入口；接口默认实现直接抛出 `UnsupportedOperationException`。 |
| `public void refresh() throws Exception` | 执行刷新或刷盘。 |
| `public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) throws Exception` | 向指定 collection 写入原始数据。 |
| `public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | 写入实体。 |
| `public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | 写入关系。 |
| `public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | 写入事件。 |
| `public boolean isEmpty(String collection) throws Exception` | 判断指定 collection 是否为空。 |
| `public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors) throws Exception` | 依据 ID 或表达式查询数据。 |
| `public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) throws Exception` | 删除数据并返回删除结果。 |
| `public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection, Object rankerConfig, int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields, List<Float> queryEmbedding, Map<String, Object> kwargs) throws Exception` | 执行图检索。 |
| `public void attachEmbedder(Embedding embedder)` | 绑定嵌入器。 |
| `public void close() throws Exception` | 关闭资源。 |
