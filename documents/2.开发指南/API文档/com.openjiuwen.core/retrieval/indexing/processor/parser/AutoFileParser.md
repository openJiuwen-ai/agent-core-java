# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser

## class AutoFileParser

```java
public class AutoFileParser extends Parser
```

File parser router based on file extension.

## Methods

| Signature | Description |
| --- | --- |
| `public static void registerNewParser(String extension, Supplier<? extends Parser> supplier)` | Execute `registerNewParser`. |
| `public static List<String> getSupportedFormats()` | Return the supported file extensions. |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `AutoFileParserTest.java`.
