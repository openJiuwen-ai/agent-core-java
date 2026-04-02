# com.openjiuwen.core.foundation.llm.output_parsers.MarkdownContent

## class MarkdownContent

```java
public class MarkdownContent
```

Structured representation of Markdown content.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `rawContent` | `String` | Stored `rawContent` value. |
| `elements` | `List<MarkdownElement>` | Stored `elements` value. |
| `headers` | `List<Map<String, Object>>` | Stored `headers` value. |
| `codeBlocks` | `List<Map<String, Object>>` | Stored `codeBlocks` value. |
| `links` | `List<Map<String, Object>>` | Stored `links` value. |
| `images` | `List<Map<String, Object>>` | Stored `images` value. |
| `tables` | `List<String>` | Stored `tables` value. |
| `lists` | `List<String>` | Stored `lists` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MarkdownContent()` | Create a new `MarkdownContent` instance. |
| `public MarkdownContent(String rawContent)` | Create a new `MarkdownContent` instance. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
