# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser

## class AutoParser

```java
public class AutoParser extends Parser
```

Top-level parser that routes between file and URL parsers.

## Constructors

| Signature | Description |
| --- | --- |
| `public AutoParser()` | Create a new `AutoParser` instance. |
| `public AutoParser(Parser linkParser, Parser fileParser)` | Create a new `AutoParser` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `AutoParserTest.java`.
