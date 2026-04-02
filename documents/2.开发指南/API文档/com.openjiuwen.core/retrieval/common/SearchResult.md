# com.openjiuwen.core.retrieval.common.SearchResult

## class SearchResult

```java
public class SearchResult
```

Raw search result.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | id. |
| `text` | `String` | text. |
| `score` | `double` | score. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SearchResult()` | Create a new `SearchResult` instance. |
| `public SearchResult(String id, String text, double score)` | Create a new `SearchResult` instance. |
| `public SearchResult(String id, String text, double score, Map<String, Object> metadata)` | Create a new `SearchResult` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setId(String id)` | Update the id. |
| `public void setText(String text)` | Update the text. |
| `public void setMetadata(Map<String, Object> metadata)` | Update the metadata. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `InMemoryVectorStoreTest.java`, `MilvusVectorStoreTest.java`, `PGVectorStoreTest.java`, `RetrievalCoreTest.java`, `RetrieverDefaultMethodTest.java`.
