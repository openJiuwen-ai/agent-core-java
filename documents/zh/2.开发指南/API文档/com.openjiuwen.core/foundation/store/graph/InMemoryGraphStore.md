# com.openjiuwen.core.foundation.store.graph.InMemoryGraphStore

## class InMemoryGraphStore

```java
public class InMemoryGraphStore implements GraphStore
```

图存储的内存实现。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static GraphStore fromConfig(GraphConfig config)` | 根据配置创建内存图存储实例。 |
| `public GraphConfig getConfig()` | 返回当前配置。 |
| `public ExecutorService getEmbedExecutor()` | 返回嵌入线程池。 |
| `public Embedding getEmbedder()` | 返回当前嵌入器。 |
| `public void refresh()` | 当前实现为空操作。 |
| `public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert)` | 向指定 collection 写入原始记录。 |
| `public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed)` | 把 `Map` 形态实体写入 `entities` collection。 |
| `public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed)` | 把 `Map` 形态关系写入 `relations` collection。 |
| `public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed)` | 把 `Map` 形态事件写入 `episodes` collection。 |
| `public boolean isEmpty(String collection)` | 判断 collection 是否为空。 |
| `public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors)` | 根据 `id` 列表执行简单查询。 |
| `public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr)` | 根据 `id` 列表删除数据并返回删除数量。 |
| `public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection, Object rankerConfig, int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields, List<Float> queryEmbedding, Map<String, Object> kwargs)` | 执行简化版搜索并返回前 `k` 条记录。 |
| `public void attachEmbedder(Embedding embedder)` | 绑定嵌入器。 |
| `public void close()` | 关闭线程池并清空内存数据。 |

## 使用说明

- 该实现主要用于轻量或内存场景，不提供完整图数据库搜索能力。
- `query` 与 `delete` 的主要过滤条件是记录中的 `id` 字段。
