# com.openjiuwen.core.foundation.store.EmbeddingConfig

## class EmbeddingConfig

```java
public class EmbeddingConfig
```

嵌入模型配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `modelName` | `String` | `-` | 模型名称，不能为空白。 |
| `baseUrl` | `String` | `-` | 服务地址，不能为空白。 |
| `apiKey` | `String` | `null` | 可选的接口密钥。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public EmbeddingConfig(String modelName, String baseUrl, String apiKey)` | 完整指定模型名称、服务地址与密钥。 |
| `public EmbeddingConfig(String modelName, String baseUrl)` | 不提供密钥时的便捷构造。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getModelName()` | 返回模型名称。 |
| `public String getBaseUrl()` | 返回服务地址。 |
| `public String getApiKey()` | 返回密钥；未设置时为 `null`。 |
