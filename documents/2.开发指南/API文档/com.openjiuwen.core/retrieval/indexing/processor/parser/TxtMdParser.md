# com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser

## class TxtMdParser

```java
public class TxtMdParser extends Parser
```

TXT/MD file parser aligned with the Python TxtMdParser behavior.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `TxtMdParserTest.java`.
