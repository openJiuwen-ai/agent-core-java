# com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient

## class BaseModelClient

```java
public abstract class BaseModelClient
```

LLM Model Client abstract base class.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `modelConfig` | `ModelRequestConfig` | Stored `modelConfig` value. |
| `modelClientConfig` | `ModelClientConfig` | Stored `modelClientConfig` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)` | Initialize the model client. |

## Methods

| Signature | Description |
| --- | --- |
| `protected String getClientName()` | Get client name for error messages. |
| `protected void validateConfig()` | Validate configuration parameters. |
| `protected List<Map<String, Object>> convertMessagesToDict(Object messages)` | Convert messages to a list of dicts in OpenAI format. |
| `protected List<Map<String, Object>> convertToolsToDict(Object tools)` | Convert tools to OpenAI format. |
| `protected Map<String, Object> buildRequestParams(Object messages, Object tools, Double temperature, Double topP, String model, String stop, Integer maxTokens, boolean stream, Map<String, Object> extraKwargs)` | Build OpenAI-compatible request parameters. |
| `public abstract AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Invoke the LLM (synchronous, blocking via virtual thread). |
| `public abstract Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Stream invoke the LLM. |
| `public abstract ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | Generate an image from a text prompt. |
| `public abstract AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | Generate speech audio from text. |
| `public abstract VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | Generate video from a text prompt. |
