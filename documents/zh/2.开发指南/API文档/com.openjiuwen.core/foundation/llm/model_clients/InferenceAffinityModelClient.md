# com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient

## 类 InferenceAffinityModelClient

```java
public class InferenceAffinityModelClient extends BaseModelClient
```

实现 InferenceAffinity 风格的对话客户端，支持 cache sharing 与 release 能力。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final HttpClient httpClient` | 保存 `httpClient` 相关状态或配置。 |
| `private final class StreamingChunkIterator implements Iterator<AssistantMessageChunk> {` | 保存 `AssistantMessageChunk` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InferenceAffinityModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {` | 构造 `InferenceAffinityModelClient` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起同步模型调用。 |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起流式模型调用。 |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) {` | 发起图像生成请求。 |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) {` | 发起语音生成请求。 |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) {` | 发起视频生成请求。 |
| `public boolean release(String sessionId, Object messages, int messagesReleasedIndex, Object tools, Integer toolsReleasedIndex, String model) throws Exception {` | 释放 InferenceAffinity 会话及已缓存的上下文。 |

## 说明

- 所有签名均以当前 Java 源码为准。
