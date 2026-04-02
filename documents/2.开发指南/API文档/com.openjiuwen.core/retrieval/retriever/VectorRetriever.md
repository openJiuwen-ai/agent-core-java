# com.openjiuwen.core.retrieval.retriever.VectorRetriever

## class VectorRetriever

```java
public class VectorRetriever extends AbstractStoreBackedRetriever
```

Pure vector retriever.

## Constructors

| Signature | Description |
| --- | --- |
| `public VectorRetriever(VectorStore vectorStore, Embedding embedModel)` | Create a dense retriever backed by the supplied vector store and embedding model. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | Run dense retrieval in `vector` mode, then fall back to `vectorStore.sparseSearch(...)` when the dense search returns no rows. |
| `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | Return raw `SearchResult` objects without converting them to `RetrievalResult` records. |
| `public boolean supportsMode(String mode)` | Return `true` only for `vector` mode. |

## Notes

- `retrieve(...)` and `retrieveSearchResults(...)` both require a non-null embedding model and reuse the optional `filters` map from `options`.
- Related tests: `RetrievalCoreTest.java`.
