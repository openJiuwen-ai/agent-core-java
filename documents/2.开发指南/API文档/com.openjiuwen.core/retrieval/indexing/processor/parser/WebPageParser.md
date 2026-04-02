# com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser

## class WebPageParser

```java
public class WebPageParser extends Parser
```

Generic web page parser.

## Constructors

| Signature | Description |
| --- | --- |
| `public WebPageParser()` | Create a new `WebPageParser` instance. |
| `public WebPageParser(HttpClient httpClient)` | Create a new `WebPageParser` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `WebPageParserTest.java`.
