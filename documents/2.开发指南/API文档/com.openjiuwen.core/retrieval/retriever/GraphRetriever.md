# com.openjiuwen.core.retrieval.retriever.GraphRetriever

## 类 GraphRetriever

```java
public class GraphRetriever extends AbstractRetriever
```

`GraphRetriever` 组合 chunk 检索、triple 检索和图扩展逻辑，用于在图化知识库上执行多跳检索。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever)` | 使用固定的 chunk/triple 检索器。 |
| `public GraphRetriever(VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | 基于 `VectorStore` 按需动态创建 chunk/triple 检索器。 |
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever, VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | 同时支持固定检索器与动态创建参数。 |

## 公开方法

### `public void setIndexType(String indexType)`

显式设置当前图检索器的索引类型，影响允许的 `mode` 集合。

### `public String getIndexType()`

返回当前索引类型；未设置时默认返回 `"hybrid"`。

### `public boolean supportsMode(String mode)`

判断 `mode` 是否与当前索引类型兼容：

- `vector` 索引仅支持 `vector`
- `bm25` 索引仅支持 `sparse`
- 其他索引支持 `vector`、`sparse`、`hybrid`

### `public Retriever getRetrieverForMode(String mode, boolean isChunk)`

返回指定模式下的 chunk 或 triple 检索器。

**说明：**

- 优先复用构造时提供的固定检索器。
- 未提供固定检索器时，会基于 `vectorStore.withCollection(...)` 动态创建 `VectorRetriever`、`SparseRetriever` 或 `HybridRetriever`。
- 动态创建 `vector` / `hybrid` 模式检索器时需要 `embedModel`；缺失会抛异常。

### `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

先执行 chunk 检索，再根据 `graph_hops` 触发图扩展，最终返回融合后的 passage 结果。

**异常：**

- `scoreThreshold` 仅允许在 `mode = "vector"` 下使用。
- 模式不兼容、缺少 `vectorStore`、缺少 collection 名称或缺少 `embedModel` 时会抛出 retrieval 领域异常。

### `public List<RetrievalResult> graphExpansion(String query, List<RetrievalResult> chunks, List<RetrievalResult> triples, Integer topK, String mode, Map<String, Object> options)`

基于已有 chunk 与 triple 做多跳扩展；`triples` 为空时会自动回查 triple 集合。

### `public void close()`

分别关闭 `chunkRetriever` 与 `tripleRetriever`，并吞掉关闭异常。

## 实现说明

- 当图扩展失败、没有 beam、或没有有效 triple 时，会回退为原始 chunk 结果。
- `graphExpansion(...)` 在 `chunks` 为空且 `mode = "sparse"` 时，会再调用一次 `sparse` chunk 检索做回退。
- 内部通过 `TripleBeamSearch`、`VectorStore.queryByFilters(...)` 与 `FusionUtils.rrfFusionRetrieval(...)` 组织多跳结果。
