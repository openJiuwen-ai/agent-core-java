# com.openjiuwen.core.retrieval.indexing.processor.parser.Parser

## class Parser

```java
public abstract class Parser implements Processor<String, List<Document>>
```

Document parser abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Execute `parse`. |
| `public boolean supports(String doc)` | Execute `supports`. |
| `public List<Document> process(String input, Map<String, Object> options)` | Execute `process`. |

## Notes

- Related tests: `AutoFileParserTest.java`, `AutoLinkParserTest.java`, `AutoParserTest.java`, `ExcelParserTest.java`, `ImageParserTest.java`, `JsonParserTest.java`.
