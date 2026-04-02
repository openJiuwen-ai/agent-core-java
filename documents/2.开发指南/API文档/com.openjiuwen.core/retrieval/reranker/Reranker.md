# com.openjiuwen.core.retrieval.reranker.Reranker

## interface Reranker

```java
public interface Reranker
```

Reranker abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | Rerank candidates and return a ranked list. |
| `default Map<String, Double> rerankScores(String query, List<?> documents)` | Rerank documents and return a mapping from document identifier to relevance score. |
| `default Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options)` | Rerank documents and return a mapping from document identifier to relevance score. |

## Notes

- Related tests: `ChatRerankerTest.java`, `LexicalRerankerTest.java`, `StandardRerankerTest.java`.
