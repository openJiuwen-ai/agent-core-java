# com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser

## class JsonOutputParser

```java
public class JsonOutputParser extends BaseOutputParser
```

JSON output parser that extracts JSON from LLM text output.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MAPPER` | `ObjectMapper` | Stored `MAPPER` value. |
| `JSON_CODE_BLOCK` | `Pattern` | Stored `JSON_CODE_BLOCK` value. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object parse(Object inputs)` | Execute `parse`. |
| `public Iterator<Object> streamParse(Iterator<?> streamingInputs)` | Execute `streamParse`. |

## Notes

- `JsonOutputParserTest` covers direct JSON parsing, fenced blocks, and incremental stream parsing.
