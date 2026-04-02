# com.openjiuwen.core.retrieval.retriever.Retriever

## interface Retriever

```java
public interface Retriever extends AutoCloseable
```

Unified abstraction for retrieval components that return `RetrievalResult` items and optional raw `SearchResult` views.

## Methods

| Signature | Description |
| --- | --- |
| `List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | Execute retrieval with the requested mode and optional score threshold. |
| `List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | Run retrieval for a batch of queries and preserve per-query result lists. |
| `default List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | Adapt `retrieve(...)` output into `SearchResult` records, preferring `chunk_id`, then `doc_id`, then a text hash as the result id. |
| `default List<RetrievalResult> retrieve(String query)` | Convenience overload that uses `topK = 5`, `mode = "hybrid"`, and empty options. |
| `default List<RetrievalResult> retrieve(String query, int topK)` | Convenience overload that uses the requested `topK`, `mode = "hybrid"`, and empty options. |
| `default boolean supportsMode(String mode)` | Report whether the implementation accepts the requested retrieval mode. The default implementation returns `true`. |
| `default String getIndexType()` | Return the retriever index type. The default value is `"hybrid"`. |
| `default void close()` | Release owned resources. The default implementation is a no-op. |

## Notes

- `RetrieverDefaultMethodTest.java` verifies that `retrieveSearchResults(...)` falls back to `retrieve(...)` and keeps the returned `chunk_id` as the search-result id.
