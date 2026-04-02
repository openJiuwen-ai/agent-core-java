# com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult

## class MultiKBRetrievalResult

```java
public class MultiKBRetrievalResult extends RetrievalResult
```

Retrieval result aggregated across multiple knowledge bases.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `rawScore` | `double` | raw score. |
| `rawScoreScaled` | `double` | raw score scaled. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MultiKBRetrievalResult(String text, double score, double rawScore, double rawScoreScaled, List<String> kbIds, Map<String, Object> metadata)` | Create a new `MultiKBRetrievalResult` instance. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `KnowledgeBaseTest.java`, `RetrievalCoreTest.java`.
