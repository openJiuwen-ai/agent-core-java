# com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient

## class InferenceAffinityModelClient

```java
public class InferenceAffinityModelClient extends BaseModelClient
```

Inference Affinity (vLLM-style) client with cache sharing and release support.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MAPPER` | `ObjectMapper` | Stored `MAPPER` value. |
| `httpClient` | `HttpClient` | Stored `httpClient` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public InferenceAffinityModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | Create a new `InferenceAffinityModelClient` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `protected String getClientName()` | Return the `clientName` value. |
| `protected void validateConfig()` | Execute `validateConfig`. |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Execute a non-streaming LLM request. |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Execute a streaming LLM request. |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)` | Generate an image response. |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs)` | Generate a speech response. |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs)` | Generate a video response. |
| `public boolean release(String sessionId, Object messages, int messagesReleasedIndex, Object tools, Integer toolsReleasedIndex, String model) throws Exception` | Execute `release`. |
