# com.openjiuwen.core.retrieval.query_rewriter.QueryRewriter

## class QueryRewriter

```java
public class QueryRewriter
```

Query rewriter with template loading, JSON parsing, and optional context-aware compression.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `llmClient` | `final BaseModelClient` | llm client. |
| `context` | `final ModelContext` | context. |
| `compressRange` | `final int` | compress range. |
| `promptLang` | `final String` | prompt lang. |

## Constructors

| Signature | Description |
| --- | --- |
| `public QueryRewriter(BaseModelClient llmClient)` | Create a new `QueryRewriter` instance. |
| `public QueryRewriter(BaseModelClient llmClient, ModelContext context, int compressRange, String promptLang)` | Create a new `QueryRewriter` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String rewrite(String query, List<RetrievalResult> results)` | Execute `rewrite`. |
| `public Map<String, Object> rewrite(String query)` | Execute `rewrite`. |
| `ensureLlm()` | Execute `ensureLlm`. |
| `public String msgToText(List<BaseMessage> messages)` | Execute `msgToText`. |
| `List<String> lines = new ArrayList<>(source.size())` | Execute `size`. |
| `int objectStart = content.indexOf('{')` | Execute `indexOf`. |
| `String first = results.getFirst().getText()` | Return the first. |

## Notes

- Related tests: `QueryRewriterTest.java`.
