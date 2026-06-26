# com.openjiuwen.core.foundation.llm.model_clients.OpenAiModelClientFactory

## 类 OpenAiModelClientFactory

```java
public class OpenAiModelClientFactory implements Model.ModelClientFactory
```

创建 OpenAI provider 客户端的默认工厂。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String providerName() {` | 返回当前工厂支持的 provider 名称。 |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {` | 创建对应的模型客户端实例。 |

## 说明

- 所有签名均以当前 Java 源码为准。
