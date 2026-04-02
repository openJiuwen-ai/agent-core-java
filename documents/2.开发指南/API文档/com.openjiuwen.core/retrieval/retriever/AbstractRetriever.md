# com.openjiuwen.core.retrieval.retriever.AbstractRetriever

## 类 AbstractRetriever

```java
public abstract class AbstractRetriever implements Retriever
```

`AbstractRetriever` 为 `Retriever` 提供批量检索默认实现，按输入顺序逐条调用单条 `retrieve(...)`。

## 公开方法

### `public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)`

按顺序对 `queries` 中的每个问题调用 `retrieve(query, topK, null, mode, options)`，并返回二维结果列表。

**参数：**

- `queries`：待批量检索的问题列表；为 `null` 时直接返回空列表。
- `topK`：每个问题保留的结果数量。
- `mode`：下游检索模式。
- `options`：透传给具体实现的附加参数。

**返回：**

- 与 `queries` 顺序一致的检索结果集合；`queries == null` 时返回空列表。
