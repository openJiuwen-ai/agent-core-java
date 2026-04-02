# com.openjiuwen.core.foundation.llm.model_clients.DashScopeModelClient

## class DashScopeModelClient

```java
public class DashScopeModelClient extends OpenAiCompatibleModelClient
```

Alibaba Cloud DashScope Model Client.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MAPPER` | `ObjectMapper` | Stored `MAPPER` value. |
| `DASHSCOPE_VOICES` | `List<String>` | Stored `DASHSCOPE_VOICES` value. |
| `multiModalHttpClient` | `HttpClient` | Stored `multiModalHttpClient` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public DashScopeModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | Create a new `DashScopeModelClient` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `protected String getClientName()` | Return the `clientName` value. |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | Generate an image response. |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | Generate a speech response. |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | Generate a video response. |
