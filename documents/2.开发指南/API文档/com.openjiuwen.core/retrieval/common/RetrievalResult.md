# com.openjiuwen.core.retrieval.common.RetrievalResult

## class RetrievalResult

```java
public class RetrievalResult
```

User-facing retrieval result.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `text` | `String` | text. |
| `score` | `double` | score. |
| `docId` | `String` | doc id. |
| `chunkId` | `String` | chunk id. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RetrievalResult()` | Create a new `RetrievalResult` instance. |
| `public RetrievalResult(String text, double score)` | Create a new `RetrievalResult` instance. |
| `public RetrievalResult(String text, double score, Map<String, Object> metadata, String docId, String chunkId)` | Create a new `RetrievalResult` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setText(String text)` | Update the text. |
| `public void setMetadata(Map<String, Object> metadata)` | Update the metadata. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `ChatRerankerTest.java`, `KnowledgeBaseTest.java`, `LexicalRerankerTest.java`, `QueryRewriterTest.java`, `RetrievalCoreTest.java`, `RetrieverDefaultMethodTest.java`.
