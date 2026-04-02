# com.openjiuwen.core.retrieval.reranker.StandardReranker

## class StandardReranker

```java
public class StandardReranker implements Reranker
```

Remote reranker implementation aligned with Python's StandardReranker behavior.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `ENDPOINT` | `static final String` | `"/rerank"` | endpoint. |
| `QUERY_TEMPLATE` | `static final String` | `"<Instruct>: %s\n<Query>: %s\n"` | query template. |
| `DEFAULT_INSTRUCT` | `static final String` | `"Given a search query, retrieve relevant candidates that answer the query."` | default instruct. |
| `config` | `final RerankerConfig` | `-` | config. |
| `modelName` | `final String` | `-` | model name. |
| `apiKey` | `final String` | `-` | api key. |
| `apiUrl` | `final String` | `-` | api url. |
| `maxRetries` | `final int` | `-` | max retries. |
| `headers` | `final Map<String, String>` | `-` | headers. |
| `httpClient` | `final HttpClient` | `-` | http client. |

## Constructors

| Signature | Description |
| --- | --- |
| `public StandardReranker(RerankerConfig config)` | Create a new `StandardReranker` instance. |
| `public StandardReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient)` | Create a new `StandardReranker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options)` | Rerank scores. |
| `List<Double> scores = rerankOrderedScores(query, candidates.stream().map(RetrievalResult::getText).toList(), Boolean.TRUE, Map.of())` | Rerank ordered scores. |
| `protected Map<String, Object> buildRequestPayload(String query, List<String> documents, Object instruct, Map<String, Object> options)` | Execute `buildRequestPayload`. |
| `protected String getModelName()` | Return the model name. |
| `protected List<Double> parseOrderedScores(JsonNode response, int documentCount)` | Parse ordered scores. |
| `JsonNode results = response.path("output").isObject() ? response.path("output").path("results") : response.path("results")` | Execute `path`. |

## Notes

- Related tests: `StandardRerankerTest.java`.
