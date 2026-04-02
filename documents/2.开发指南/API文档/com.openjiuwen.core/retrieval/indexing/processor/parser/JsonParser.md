# com.openjiuwen.core.retrieval.indexing.processor.parser.JsonParser

## class JsonParser

```java
public class JsonParser extends Parser
```

JSON file parser that returns formatted JSON text when possible.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Execute `parse`. |
| `public boolean supports(String doc)` | Execute `supports`. |

## Notes

- Related tests: `JsonParserTest.java`.
