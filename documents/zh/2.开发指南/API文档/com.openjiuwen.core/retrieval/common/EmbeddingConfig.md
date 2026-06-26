# com.openjiuwen.core.foundation.store.EmbeddingConfig

## 类 EmbeddingConfig

```java
public class EmbeddingConfig
```

embedding 模型访问配置，定义模型名、服务地址与可选 API key。

## 说明

- `modelName` 与 `baseUrl` 不能为空白。
- 支持 `model_name/modelName`、`base_url/baseUrl`、`api_key/apiKey` 别名。
- `apiKey` 可为空。
