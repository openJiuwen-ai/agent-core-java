# com.openjiuwen.core.foundation.llm.model_clients.DashScopeModelClient

## 类 DashScopeModelClient

```java
public class DashScopeModelClient extends OpenAiCompatibleModelClient
```

面向 DashScope provider 的客户端实现。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final HttpClient multiModalHttpClient` | 保存 `multiModalHttpClient` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DashScopeModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {` | 构造 `DashScopeModelClient` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception {` | 发起图像生成请求。 |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception {` | 发起语音生成请求。 |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {` | 发起视频生成请求。 |

## 说明

- 所有签名均以当前 Java 源码为准。
