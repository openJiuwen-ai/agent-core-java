# com.openjiuwen.core.foundation.llm.output_parsers.MarkdownOutputParser

## class MarkdownOutputParser

```java
public class MarkdownOutputParser extends BaseOutputParser
```

Markdown output parser that extracts structured elements from LLM output.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `HEADER_PATTERN` | `Pattern` | Stored `HEADER_PATTERN` value. |
| `CODE_BLOCK_PATTERN` | `Pattern` | Stored `CODE_BLOCK_PATTERN` value. |
| `INLINE_CODE_PATTERN` | `Pattern` | Stored `INLINE_CODE_PATTERN` value. |
| `IMAGE_PATTERN` | `Pattern` | Stored `IMAGE_PATTERN` value. |
| `LINK_PATTERN` | `Pattern` | Stored `LINK_PATTERN` value. |
| `UNORDERED_LIST_PATTERN` | `Pattern` | Stored `UNORDERED_LIST_PATTERN` value. |
| `ORDERED_LIST_PATTERN` | `Pattern` | Stored `ORDERED_LIST_PATTERN` value. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object parse(Object inputs)` | Execute `parse`. |
| `public Iterator<Object> streamParse(Iterator<?> streamingInputs)` | Execute `streamParse`. |
