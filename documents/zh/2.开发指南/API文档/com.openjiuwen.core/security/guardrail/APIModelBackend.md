# com.openjiuwen.core.security.guardrail.APIModelBackend

## class APIModelBackend

```java
public class APIModelBackend extends GuardrailBackend
```

`APIModelBackend` 调用远端模型 API 检测文本，并把响应交给 `ModelOutputParser` 解析为 `RiskAssessment`。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `APIModelBackend(APIModelBackendConfig config)` | 从配置对象读取 API 地址、parser、密钥和超时。 |
| `APIModelBackend(String apiUrl, ModelOutputParser parser, String apiKey, double timeout, String riskType)` | 直接指定远端检测参数。 |

## Methods

### `RiskAssessment analyze(GuardrailContext ctx)`

- 上下文文本为空时直接返回安全结果。
- `parser == null` 时抛 `IllegalStateException`。
- 否则调用 `callApi(text)`，再用 parser 解析响应。

### `protected Object callApi(String text)`

向 `apiUrl` 发送 JSON POST：

```json
{"text": "..."}
```

如果设置了 `apiKey`，会添加 `Authorization: Bearer <apiKey>`。HTTP 状态码大于等于 400 时抛 `IllegalStateException`。
