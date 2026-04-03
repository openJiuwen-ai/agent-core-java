# com.openjiuwen.core.foundation.llm.InferenceAffinityModel

## 类 InferenceAffinityModel

```java
public class InferenceAffinityModel
```

统一 InferenceAffinity 会话调用入口，向 `InferenceAffinityModelClient` 透传 session 与缓存共享参数。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final ModelRequestConfig modelConfig` | 保存 `modelConfig` 配置。 |
| `private final ModelClientConfig modelClientConfig` | 保存 `modelClientConfig` 配置。 |
| `private final InferenceAffinityModelClient client` | 持有当前 provider 对应的客户端实例。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {` | 构造 `InferenceAffinityModel` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ModelRequestConfig getModelConfig() {` | 返回 `modelConfig` 属性。 |
| `public ModelClientConfig getModelClientConfig() {` | 返回 `modelClientConfig` 属性。 |
| `public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception {` | 发起同步模型调用。 |
| `public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception {` | 发起流式模型调用。 |
| `public boolean release(String sessionId, List<?> messages, int messagesReleasedIndex, List<?> tools, Integer toolsReleasedIndex, String model) throws Exception {` | 释放 InferenceAffinity 会话及已缓存的上下文。 |

## 说明

- 所有签名均以当前 Java 源码为准。
