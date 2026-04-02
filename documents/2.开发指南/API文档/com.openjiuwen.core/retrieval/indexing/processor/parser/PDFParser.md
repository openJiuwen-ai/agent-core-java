# com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser

## class PDFParser

```java
public class PDFParser extends Parser
```

PDF parser with optional image caption extraction.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `PDFParserTest.java`.
