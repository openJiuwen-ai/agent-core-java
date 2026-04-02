# com.openjiuwen.core.retrieval.common.Document

## class Document

```java
public class Document
```

Document model.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `text` | `String` | text. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Document()` | Create a new `Document` instance. |
| `public Document(String text)` | Create a new `Document` instance. |
| `public Document(String id, String text)` | Create a new `Document` instance. |
| `public Document(String id, String text, Map<String, Object> metadata)` | Create a new `Document` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `setText(text)` | Update the text. |
| `public void setMetadata(Map<String, Object> metadata)` | Update the metadata. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `AutoFileParserTest.java`, `AutoLinkParserTest.java`, `AutoParserTest.java`, `ExcelParserTest.java`, `InMemoryIndexerTest.java`, `JsonParserTest.java`.
