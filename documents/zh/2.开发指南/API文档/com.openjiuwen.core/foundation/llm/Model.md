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
| `private final ModelClient client` | 持有当前 provider 对应的客户端实例。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public interface ModelClientFactory {` | 定义 provider 工厂 SPI 合约。 |
| `public interface ModelClient {` | 定义客户端调用与可选观测能力；`getCircuitBreakerSnapshot()` 默认返回 `Optional.empty()`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {` | 构造 `Model` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void registerFactory(ModelClientFactory factory) {` | 向工厂注册表加入 provider 实现。 |
| `public Optional<ModelCircuitBreakerSnapshot> getCircuitBreakerSnapshot()` | 读取当前客户端的熔断器诊断快照，观测能力不可用时返回空。 |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起同步模型调用。 |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) throws Exception {` | 发起流式模型调用。 |
| `public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) throws Exception {` | 发起图像生成请求。 |
| `public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs) throws Exception {` | 发起语音生成请求。 |
| `public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {` | 发起视频生成请求。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `ModelFactoryRegistrationTest` 覆盖 provider 工厂注册与大小写 provider 名称匹配。

## 读取熔断器状态

从 0.1.17 起，可以通过 `Model` 读取不可变快照，无需取得或操作内部熔断器：

```java
model.getCircuitBreakerSnapshot().ifPresent(snapshot -> {
    System.out.println("open=" + snapshot.open());
    System.out.println("consecutiveFailures=" + snapshot.consecutiveFailures());
});
```

`ModelCircuitBreakerSnapshot` 为公开 record，包含以下字段：

| 字段 | 含义 |
| --- | --- |
| `boolean open` | 采样时是否处于快速失败窗口。窗口到期后为 `false`，没有独立的 HALF_OPEN 状态。 |
| `int consecutiveFailures` | 自最近一次成功以来累计的计入熔断的失败数。成功清零，忽略的错误和窗口到期均不清零。 |

已分类的限流（如 HTTP 429）和临时错误（如 HTTP 503）计入失败；未分类错误沿用连接错误判断。
认证、配额、上下文过长和取消等错误不会增加或清空已有计数。普通请求在重试耗尽后只计一次失败；
因熔断快速失败的调用不增加计数。流式调用沿用现有首段重试窗口的计数方式，观察最终状态前应完成流的消费。

`OpenAIModelClient` 及其子类（包括 `OpenAiCompatibleModelClient`）提供此能力，也可以直接调用客户端的
`getCircuitBreakerSnapshot()`。直接持有 `ModelCircuitBreaker` 时可以调用 `snapshot()`。
其他客户端默认返回 `Optional.empty()`，包括尚未转发观测能力的委托型客户端；空值表示观测不支持或不可用，
不能据此断言内部不存在熔断器。自定义 `ModelClient` 可以覆盖默认方法提供自己的快照。

保存的快照不会随后续调用变化。两个字段分别读取，在并发更新期间不保证来自同一瞬间；精确断言应在相关调用完成后采样。
该 API 用于诊断，不提供熔断器控制能力。失败数会重置，因此若接入监控，应作为 gauge，而非单调递增的 counter。
