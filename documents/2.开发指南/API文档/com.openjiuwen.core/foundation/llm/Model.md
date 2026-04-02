# com.openjiuwen.core.foundation.llm.Model

## class Model

```java
public class Model
```

Unified LLM invocation entry point.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `FACTORY_REGISTRY` | `Map<String, ModelClientFactory>` | Static registry populated from ServiceLoader + manual registration. |
| `modelConfig` | `ModelRequestConfig` | Stored `modelConfig` value. |
| `modelClientConfig` | `ModelClientConfig` | Stored `modelClientConfig` value. |
| `client` | `BaseModelClient` | Stored `client` value. |

## Nested Types

| Declaration | Description |
| --- | --- |
| `public interface ModelClientFactory` | SPI-based registry for model client factories. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig)` | Construct a Model. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void registerFactory(ModelClientFactory factory)` | Register a model client factory programmatically. |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Execute a non-streaming LLM request. |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | Execute a streaming LLM request. |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | Generate an image response. |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | Generate a speech response. |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | Generate a video response. |

## Notes

- `ModelFactoryRegistrationTest` verifies the built-in provider registration path used by `Model`.
