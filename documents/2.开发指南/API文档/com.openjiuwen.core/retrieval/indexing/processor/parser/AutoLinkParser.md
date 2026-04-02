# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser

## class AutoLinkParser

```java
public class AutoLinkParser extends Parser
```

URL parser router.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `HTTP_URL_PATTERN` | `Pattern` | Regular-expression pattern used by this type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public AutoLinkParser()` | Create a new `AutoLinkParser` instance. |
| `public AutoLinkParser(List<Route> routes)` | Create a new `AutoLinkParser` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Nested Types

| Signature | Description |
| --- | --- |
| `public record Route(Predicate<String> matcher, Parser parser)` | - |

## Notes

- Related tests: `AutoLinkParserTest.java`.
