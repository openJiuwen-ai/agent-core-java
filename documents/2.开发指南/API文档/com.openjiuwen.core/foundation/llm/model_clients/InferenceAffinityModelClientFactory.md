# com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClientFactory

## 类 InferenceAffinityModelClientFactory

```java
public class InferenceAffinityModelClientFactory implements Model.ModelClientFactory
```

创建 `InferenceAffinityModelClient` 实例的工厂。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final String providerName` | 保存 `providerName` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InferenceAffinityModelClientFactory(String providerName) {` | 构造 `InferenceAffinityModelClientFactory` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String providerName() {` | 返回当前工厂支持的 provider 名称。 |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {` | 创建对应的模型客户端实例。 |

## 说明

- 所有签名均以当前 Java 源码为准。
