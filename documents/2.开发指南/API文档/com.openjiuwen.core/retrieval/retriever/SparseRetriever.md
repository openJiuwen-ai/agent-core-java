# com.openjiuwen.core.retrieval.retriever.SparseRetriever

## 类 SparseRetriever

```java
public class SparseRetriever extends AbstractStoreBackedRetriever
```

`SparseRetriever` 只封装 `VectorStore.sparseSearch(...)`，用于 BM25/全文检索风格的召回。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SparseRetriever(VectorStore vectorStore)` | 创建只支持 `sparse` 模式的检索器。 |

## 公开方法

### `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

调用 `vectorStore.sparseSearch(...)` 并把结果转换为 `RetrievalResult`。

**说明：**

- `options.filters` 会透传给底层存储。
- `chunkId` 直接使用 `SearchResult.id`；`docId` 从 `metadata.doc_id` 推导。
- 当 `mode` 不是 `"sparse"` 时会抛出不支持模式异常。

### `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)`

直接返回 `vectorStore.sparseSearch(...)` 的结果。

### `public boolean supportsMode(String mode)`

仅当 `mode = "sparse"` 时返回 `true`。
