# com.openjiuwen.core.foundation.llm.model_clients.OpenRouterModelClientFactory

## 类 OpenRouterModelClientFactory

```java
public class OpenRouterModelClientFactory implements Model.ModelClientFactory
```

创建 OpenRouter provider alias 对应客户端的工厂。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String providerName() {` | 返回当前工厂支持的 provider 名称。 |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {` | 创建对应的模型客户端实例。 |

## 说明

- 所有签名均以当前 Java 源码为准。
