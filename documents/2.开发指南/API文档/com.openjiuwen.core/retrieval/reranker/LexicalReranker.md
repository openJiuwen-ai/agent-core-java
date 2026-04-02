# com.openjiuwen.core.retrieval.reranker.LexicalReranker

## class LexicalReranker

```java
public class LexicalReranker implements Reranker
```

Local lexical reranker based on token overlap.

## Methods

| Signature | Description |
| --- | --- |
| `public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | Execute `rerank`. |
| `Set<String> queryTokens = tokens(query)` | Execute `tokens`. |

## Notes

- Related tests: `LexicalRerankerTest.java`, `StandardRerankerTest.java`.
