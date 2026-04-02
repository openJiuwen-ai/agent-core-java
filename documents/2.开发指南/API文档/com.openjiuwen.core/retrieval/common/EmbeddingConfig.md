# com.openjiuwen.core.retrieval.common.EmbeddingConfig

## class EmbeddingConfig

```java
public class EmbeddingConfig
```

Embedding model configuration.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `modelName` | `String` | model name. |
| `baseUrl` | `String` | base url. |
| `apiKey` | `String` | api key. |

## Constructors

| Signature | Description |
| --- | --- |
| `public EmbeddingConfig()` | Create a new `EmbeddingConfig` instance. |
| `public EmbeddingConfig(String modelName, String baseUrl)` | Create a new `EmbeddingConfig` instance. |
| `public EmbeddingConfig(String modelName, String baseUrl, String apiKey)` | Create a new `EmbeddingConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getModelName()` | Return the model name. |
| `public void setModelName(String modelName)` | Update the model name. |
| `public String getBaseUrl()` | Return the base url. |
| `public void setBaseUrl(String baseUrl)` | Update the base url. |
| `public String getApiKey()` | Return the api key. |
| `public void setApiKey(String apiKey)` | Update the api key. |

## Notes

- Related tests: `APIEmbeddingTest.java`, `ConfigTest.java`, `OpenAIEmbeddingTest.java`, `RetrievalCoreTest.java`, `VLLMEmbeddingTest.java`.
