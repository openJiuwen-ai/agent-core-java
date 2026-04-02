# com.openjiuwen.core.retrieval.utils.FusionUtils

## class FusionUtils

```java
public final class FusionUtils
```

Fusion algorithms such as reciprocal rank fusion.

## Constructors

| Signature | Description |
| --- | --- |
| `private FusionUtils()` | Create a new `FusionUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, int k)` | Execute `rrfFusionRetrieval`. |
| `public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, RRFRankConfig config)` | Execute `rrfFusionSearch`. |
| `public static List<RetrievalResult> weightedFusionRetrieval(List<List<RetrievalResult>> resultsList, WeightedRankConfig config)` | Execute `weightedFusionRetrieval`. |
| `double weight = weights.get(i)` | Execute `get`. |
| `double weight = weights.get(i)` | Execute `get`. |
| `List<Double> values = List.of(config.getDenseName(), config.getDenseContent(), config.getSparseContent())` | Execute `of`. |

## Notes

- Related tests: `RetrievalCoreTest.java`.
