# com.openjiuwen.core.retrieval.common.RerankerConfig

## class RerankerConfig

```java
public class RerankerConfig
```

Reranker model configuration aligned with the Python implementation.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `apiKey` | `String` | `""` | api key. |
| `apiBase` | `String` | `-` | api base. |
| `modelName` | `String` | `""` | model name. |
| `timeout` | `double` | `10.0` | timeout. |
| `temperature` | `double` | `0.95` | temperature. |
| `topP` | `double` | `0.1` | top p. |
| `yesNoIds` | `List<Integer>` | `-` | yes no ids. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RerankerConfig()` | Create a new `RerankerConfig` instance. |
| `public RerankerConfig(String apiBase)` | Create a new `RerankerConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getApiKey()` | Return the api key. |
| `public void setApiKey(String apiKey)` | Update the api key. |
| `public String getApiBase()` | Return the api base. |
| `public void setApiBase(String apiBase)` | Update the api base. |
| `public String getModelName()` | Return the model name. |
| `public void setModelName(String modelName)` | Update the model name. |
| `public double getTimeout()` | Return the timeout. |
| `public void setTimeout(double timeout)` | Update the timeout. |
| `public void setTemperature(double temperature)` | Update the temperature. |
| `public double getTopP()` | Return the top p. |
| `public void setTopP(double topP)` | Update the top p. |
| `public List<Integer> getYesNoIds()` | Return the yes no ids. |
| `public void setYesNoIds(List<Integer> yesNoIds)` | Update the yes no ids. |
| `public Map<String, Object> getExtraBody()` | Return the extra body. |
| `public void setExtraBody(Map<String, Object> extraBody)` | Update the extra body. |

## Notes

- Related tests: `ChatRerankerTest.java`, `ConfigTest.java`, `StandardRerankerTest.java`.
