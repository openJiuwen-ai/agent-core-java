# com.openjiuwen.core.foundation.llm.model_clients.SiliconFlowModelClientFactory

## 类 SiliconFlowModelClientFactory

```java
public class SiliconFlowModelClientFactory implements Model.ModelClientFactory
```

创建 SiliconFlow provider 客户端的默认工厂。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String providerName() {` | 返回当前工厂支持的 provider 名称。 |
| `public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {` | 创建对应的模型客户端实例。 |

## 说明

- 所有签名均以当前 Java 源码为准。
