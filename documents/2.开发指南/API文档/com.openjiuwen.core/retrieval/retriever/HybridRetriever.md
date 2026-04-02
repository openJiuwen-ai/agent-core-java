# com.openjiuwen.core.retrieval.retriever.HybridRetriever

## class HybridRetriever

```java
public class HybridRetriever extends AbstractStoreBackedRetriever
```

Hybrid retriever combining sparse and dense retrieval.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `alpha` | `final double` | alpha. |

## Constructors

| Signature | Description |
| --- | --- |
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel)` | Create a new `HybridRetriever` instance. |
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha)` | Create a new `HybridRetriever` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | Execute `retrieve`. |

## Notes

- Related tests: `RetrievalCoreTest.java`.
