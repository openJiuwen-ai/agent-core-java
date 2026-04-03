# com.openjiuwen.core.retrieval.retriever.Retriever

## 接口 Retriever

```java
public interface Retriever extends AutoCloseable
```

`Retriever` 定义 retrieval 子系统统一的检索接口，并提供若干默认方法。

## 抽象方法

### `List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

执行单条检索。

### `List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)`

执行批量检索。

## 默认方法

### `default List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)`

把 `retrieve(...)` 的结果映射为 `SearchResult`。

**实现细节：**

- `RetrieverDefaultMethodTest` 验证该方法优先使用 `chunkId` 作为 `SearchResult.id`。
- 当 `chunkId` 为空时回退到 `docId`。
- 当 `chunkId` 与 `docId` 都为空时，回退到 `result.getText().hashCode()` 的十六进制字符串。

### `default List<RetrievalResult> retrieve(String query)`

等价于 `retrieve(query, 5, null, "hybrid", Map.of())`。

### `default List<RetrievalResult> retrieve(String query, int topK)`

等价于 `retrieve(query, topK, null, "hybrid", Map.of())`。

### `default boolean supportsMode(String mode)`

默认返回 `true`；具体实现可收窄支持模式。

### `default String getIndexType()`

默认返回 `"hybrid"`。

### `default void close()`

默认空实现。
