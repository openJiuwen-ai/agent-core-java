# com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser

## class WeChatArticleParser

```java
public class WeChatArticleParser extends WebPageParser
```

WeChat article parser.

## Constructors

| Signature | Description |
| --- | --- |
| `public WeChatArticleParser()` | Create a new `WeChatArticleParser` instance. |
| `public WeChatArticleParser(HttpClient httpClient)` | Create a new `WeChatArticleParser` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static boolean isWechatArticleUrl(String url)` | Execute `isWechatArticleUrl`. |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `WeChatArticleParserTest.java`.
