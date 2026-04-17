# com.openjiuwen.core.retrieval.common.RerankerConfig

## 类 RerankerConfig

```java
public class RerankerConfig
```

远程重排器配置，定义 API 地址、模型名、超时、采样参数、yes/no token id 与额外请求体字段。

## 说明

- `apiBase` 不能为空白。
- 默认 `timeout = 10.0`、`temperature = 0.95`、`topP = 0.1`。
- `yesNoIds` 与 `extraBody` 会通过复制或只读视图保护内部状态。
- `ChatReranker` 会依赖 `yesNoIds` 计算 `yes/no` 概率分数。
