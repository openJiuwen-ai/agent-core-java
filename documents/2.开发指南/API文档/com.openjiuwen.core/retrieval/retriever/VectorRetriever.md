# com.openjiuwen.core.retrieval.retriever.VectorRetriever

## 类 VectorRetriever

```java
public class VectorRetriever extends AbstractStoreBackedRetriever
```

`VectorRetriever` 封装向量检索流程，并在无向量结果时自动回退到稀疏检索。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public VectorRetriever(VectorStore vectorStore, Embedding embedModel)` | 创建向量检索器。 |

## 公开方法

### `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

调用 `embedModel.embedQuery(query)` 生成查询向量，再执行 `vectorStore.search(...)`。

**说明：**

- `mode` 必须为 `"vector"`。
- `options.filters` 会透传给底层存储。
- 若向量检索无结果，会回退到 `vectorStore.sparseSearch(...)`。
- `scoreThreshold` 不为 `null` 时，只保留得分不低于阈值的结果。

### `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)`

返回底层 `SearchResult`，同样在向量结果为空时回退到稀疏检索。

### `public boolean supportsMode(String mode)`

仅支持 `"vector"` 模式。

## 异常

- `mode` 不是 `"vector"` 时抛出不支持模式异常。
- `embedModel == null` 时抛出缺少 embedding 模型异常。
