# com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser

## class WordParser

```java
public class WordParser extends Parser
```

DOCX parser with optional image caption support.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `WordParserTest.java`.
