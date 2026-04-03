# com.openjiuwen.core.retrieval.retriever.HybridRetriever

## 类 HybridRetriever

```java
public class HybridRetriever extends AbstractStoreBackedRetriever
```

`HybridRetriever` 组合 `VectorStore` 的稠密检索、稀疏检索与混合检索接口，对外统一暴露 `Retriever` 风格结果。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel)` | 使用默认 `alpha = 0.5`。 |
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha)` | 指定默认融合权重。 |

## 公开方法

### `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

根据 `mode` 调用 `vectorStore.hybridSearch(...)`、`vectorStore.search(...)` 或 `vectorStore.sparseSearch(...)`，并转换为 `RetrievalResult`。

**参数：**

- `options` 中的 `filters` 会作为过滤条件透传。
- `options` 中的 `alpha` 为数值时，可覆盖构造时的默认权重。

**异常：**

- `scoreThreshold` 仅允许在 `mode = "vector"` 下使用。
- `mode = "vector"` 且 `embedModel == null` 时抛出缺少 embedding 模型异常。
- 传入未支持的 `mode` 时抛出不支持模式异常。

### `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)`

返回底层 `SearchResult` 列表；`vector` 模式无结果时会回退到 `sparseSearch(...)`。

### `public boolean supportsMode(String mode)`

返回当前实现支持的模式集合：`hybrid`、`vector`、`sparse`。
