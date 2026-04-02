# com.openjiuwen.core.foundation.llm.Model

## 类 Model

```java
public class Model
```

统一 LLM 调用入口，按 `clientProvider` 选择 `BaseModelClient` 并转发同步、流式与多模态方法。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final ModelRequestConfig modelConfig` | 保存 `modelConfig` 配置。 |
| `private final ModelClientConfig modelClientConfig` | 保存 `modelClientConfig` 配置。 |
| `private final BaseModelClient client` | 持有当前 provider 对应的客户端实例。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public interface ModelClientFactory {` | 定义 provider 工厂 SPI 合约。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {` | 构造 `Model` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void registerFactory(ModelClientFactory factory) {` | 向工厂注册表加入 provider 实现。 |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起同步模型调用。 |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起流式模型调用。 |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception {` | 发起图像生成请求。 |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception {` | 发起语音生成请求。 |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {` | 发起视频生成请求。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `ModelFactoryRegistrationTest` 覆盖 provider 工厂注册与大小写 provider 名称匹配。
