# com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClientFactory

## class InferenceAffinityModelClientFactory

```java
public class InferenceAffinityModelClientFactory implements Model.ModelClientFactory
```

Factory for InferenceAffinity model clients.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `providerName` | `String` | Stored `providerName` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public InferenceAffinityModelClientFactory(String providerName)` | Create a new `InferenceAffinityModelClientFactory` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String providerName()` | Execute `providerName`. |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig)` | Execute `create`. |
