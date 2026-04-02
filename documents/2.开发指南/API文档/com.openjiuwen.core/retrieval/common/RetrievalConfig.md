# com.openjiuwen.core.retrieval.common.RetrievalConfig

## class RetrievalConfig

```java
public class RetrievalConfig
```

Retrieval-time options.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `topK` | `int` | `5` | top k. |
| `scoreThreshold` | `Double` | `-` | score threshold. |
| `useGraph` | `Boolean` | `-` | use graph. |
| `agentic` | `boolean` | `false` | agentic. |
| `graphExpansion` | `boolean` | `false` | graph expansion. |
| `filters` | `Map<String, Object>` | `-` | filters. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RetrievalConfig()` | Create a new `RetrievalConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public int getTopK()` | Return the top k. |
| `public void setTopK(int topK)` | Update the top k. |
| `public Double getScoreThreshold()` | Return the score threshold. |
| `public void setScoreThreshold(Double scoreThreshold)` | Update the score threshold. |
| `public void setUseGraph(Boolean useGraph)` | Update the use graph. |
| `public boolean isAgentic()` | Return whether agentic. |
| `public void setAgentic(boolean agentic)` | Update the agentic. |
| `public boolean isGraphExpansion()` | Return whether graph expansion. |
| `public void setGraphExpansion(boolean graphExpansion)` | Update the graph expansion. |
| `public Map<String, Object> getFilters()` | Return the filters. |
| `public void setFilters(Map<String, Object> filters)` | Update the filters. |

## Notes

- Related tests: `ConfigTest.java`, `KnowledgeBaseTest.java`, `RetrievalCoreTest.java`.
