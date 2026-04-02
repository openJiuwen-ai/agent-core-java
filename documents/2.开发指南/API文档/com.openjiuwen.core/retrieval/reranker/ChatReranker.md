# com.openjiuwen.core.retrieval.reranker.ChatReranker

## class ChatReranker

```java
public class ChatReranker extends StandardReranker
```

Chat-completion-based reranker aligned with Python's ChatReranker behavior.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `CHAT_ENDPOINT` | `static final String` | `"/chat/completions"` | chat endpoint. |
| `DOC_TEMPLATE` | `static final String` | `"<Document>: %s"` | doc template. |
| `SYSTEM_INSTRUCT` | `static final String` | `"Judge whether the Document meets the requirements based on the Query and the...` | system instruct. |
| `yesNoIds` | `final List<Integer>` | `-` | yes no ids. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ChatReranker(RerankerConfig config)` | Create a new `ChatReranker` instance. |
| `public ChatReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient)` | Create a new `ChatReranker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `payload.put("logit_bias", logitBias)` | Execute `put`. |
| `JsonNode logprobs = choice.path("logprobs")` | Execute `path`. |

## Notes

- Related tests: `ChatRerankerTest.java`.
