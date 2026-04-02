# com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient

## 类 BaseModelClient

```java
public abstract class BaseModelClient
```

提供 provider 客户端的公共调用骨架，统一文本、流式与多模态生成接口。

## 字段

| 声明 | 说明 |
| --- | --- |
| `protected final ModelRequestConfig modelConfig` | 保存 `modelConfig` 配置。 |
| `protected final ModelClientConfig modelClientConfig` | 保存 `modelClientConfig` 配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | 发起同步模型调用。 |
| `public abstract Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception` | 发起流式模型调用。 |
| `public abstract ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception` | 发起图像生成请求。 |
| `public abstract AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception` | 发起语音生成请求。 |
| `public abstract VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception` | 发起视频生成请求。 |

## 说明

- 所有签名均以当前 Java 源码为准。
