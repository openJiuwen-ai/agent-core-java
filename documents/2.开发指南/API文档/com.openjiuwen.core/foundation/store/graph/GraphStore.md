# com.openjiuwen.core.foundation.store.graph.GraphStore

## interface GraphStore

```java
public interface GraphStore
```

Interface defining the contract for graph vector store backends.

## Methods

| Signature | Description |
| --- | --- |
| `GraphConfig getConfig()` | Get the graph configuration. |
| `ExecutorService getEmbedExecutor()` | Get the executor for embedding tasks. |
| `Embedding getEmbedder()` | Get the optional embedding service. |
| `static GraphStore fromConfig(GraphConfig config)` | Create a backend instance from configuration. |
| `void refresh()` | Refresh / flush inserted data to database. |
| `void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) throws Exception` | Add arbitrary data into database. |
| `void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | Add entity objects to the graph store. |
| `void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | Add relation objects to the graph store. |
| `void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) throws Exception` | Add episode objects to the graph store. |
| `boolean isEmpty(String collection)` | Check if a collection is empty. |
| `List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors) throws Exception` | Query graph objects from a collection. |
| `Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) throws Exception` | Delete graph objects from a collection. |
| `Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection, Object rankerConfig, int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields, List<Float> queryEmbedding, Map<String, Object> kwargs) throws Exception` | Search for graph objects using hybrid search. |
| `void attachEmbedder(Embedding embedder)` | Attach an embedding service to the backend. |
| `void close()` | Close the backend and clean up resources. |
