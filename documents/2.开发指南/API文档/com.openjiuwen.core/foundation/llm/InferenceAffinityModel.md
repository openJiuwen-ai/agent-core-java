# com.openjiuwen.core.foundation.llm.InferenceAffinityModel

## class InferenceAffinityModel

```java
public class InferenceAffinityModel
```

Unified entry point for InferenceAffinity (vLLM-style) invocation.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `modelConfig` | `ModelRequestConfig` | Stored `modelConfig` value. |
| `modelClientConfig` | `ModelClientConfig` | Stored `modelClientConfig` value. |
| `client` | `InferenceAffinityModelClient` | Stored `client` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig)` | Create a new `InferenceAffinityModel` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public ModelRequestConfig getModelConfig()` | Return the `modelConfig` value. |
| `public ModelClientConfig getModelClientConfig()` | Return the `modelClientConfig` value. |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception` | Execute a non-streaming LLM request. |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception` | Execute a streaming LLM request. |
| `public boolean release(String sessionId, List<?> messages, int messagesReleasedIndex, List<?> tools, Integer toolsReleasedIndex, String model) throws Exception` | Execute `release`. |
