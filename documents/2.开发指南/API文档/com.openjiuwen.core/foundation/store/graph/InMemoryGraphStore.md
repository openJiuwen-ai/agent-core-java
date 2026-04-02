# com.openjiuwen.core.foundation.store.graph.InMemoryGraphStore

## class InMemoryGraphStore

```java
public class InMemoryGraphStore implements GraphStore
```

In-memory implementation of the foundation `GraphStore` contract.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `config` | `final GraphConfig` | `-` | Config. |
| `embedExecutor` | `final ExecutorService` | `-` | Embed executor. |
| `embedder` | `Embedding` | `-` | Embedder. |
| `collections` | `final Map<String, List<Map<String, Object>>>` | `new ConcurrentHashMap<>()` | Collection name -> list of data records. |

## Constructors

| Signature | Description |
| --- | --- |
| `private InMemoryGraphStore(GraphConfig config)` | Create a new `InMemoryGraphStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static GraphStore fromConfig(GraphConfig config)` | Factory method: create an InMemoryGraphStore from config. |
| `public GraphConfig getConfig()` | Return the config. |
| `public ExecutorService getEmbedExecutor()` | Return the embed executor. |
| `public Embedding getEmbedder()` | Return the embedder. |
| `public void refresh()` | Execute `refresh`. |
| `public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert)` | Add the requested value. |
| `public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed)` | Add the requested value. |
| `public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed)` | Add the requested value. |
| `public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed)` | Add the requested value. |
| `public boolean isEmpty(String collection)` | Return whether the empty is enabled. |
| `public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors)` | Execute `query`. |
| `public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr)` | Delete the requested resource. |
| `public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection, Object rankerConfig, int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields, List<Float> queryEmbedding, Map<String, Object> kwargs)` | Execute `search`. |
| `public void attachEmbedder(Embedding embedder)` | Execute `attachEmbedder`. |
| `public void close()` | Close the underlying resource. |
