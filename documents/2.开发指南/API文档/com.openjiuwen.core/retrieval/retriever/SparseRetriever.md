# com.openjiuwen.core.retrieval.retriever.SparseRetriever

## class SparseRetriever

```java
public class SparseRetriever extends AbstractStoreBackedRetriever
```

Sparse / BM25-like retriever.

## Constructors

| Signature | Description |
| --- | --- |
| `public SparseRetriever(VectorStore vectorStore)` | Create a sparse retriever backed by the supplied vector store. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | Run sparse retrieval in `sparse` mode and convert `SearchResult` rows into `RetrievalResult` values, using the result id as the chunk id. |
| `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | Return raw sparse-search results for the requested query. |
| `public boolean supportsMode(String mode)` | Return `true` only for `sparse` mode. |

## Notes

- The implementation rejects non-`sparse` modes with `RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT`.
- Related tests: `RetrievalCoreTest.java`.
