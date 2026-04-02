# com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser

## class ImageParser

```java
public class ImageParser extends Parser
```

Parser for image files using LLM captions.

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `ImageParserTest.java`.
