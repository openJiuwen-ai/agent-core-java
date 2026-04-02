# com.openjiuwen.core.retrieval.common.TextChunk

## class TextChunk

```java
public class TextChunk
```

Text chunk model.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | id. |
| `text` | `String` | text. |
| `docId` | `String` | doc id. |
| `embedding` | `List<Float>` | embedding. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TextChunk()` | Create a new `TextChunk` instance. |
| `public TextChunk(String id, String text, String docId)` | Create a new `TextChunk` instance. |
| `public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Float> embedding)` | Create a new `TextChunk` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static TextChunk fromDocument(Document document, String chunkText)` | Execute `fromDocument`. |
| `public static TextChunk fromDocument(Document document, String chunkText, String id)` | Execute `fromDocument`. |
| `public void setId(String id)` | Update the id. |
| `public void setText(String text)` | Update the text. |
| `public void setDocId(String docId)` | Update the doc id. |
| `public void setMetadata(Map<String, Object> metadata)` | Update the metadata. |
| `public void setEmbedding(List<Float> embedding)` | Update the embedding. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `InMemoryIndexerTest.java`, `LLMTripleExtractorTest.java`, `MilvusIndexerTest.java`, `RetrievalCoreTest.java`, `SimpleTripleExtractorTest.java`.
