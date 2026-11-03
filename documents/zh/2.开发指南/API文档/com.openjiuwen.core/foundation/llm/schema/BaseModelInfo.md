# com.openjiuwen.core.foundation.llm.schema.BaseModelInfo

## 类 BaseModelInfo

```java
public class BaseModelInfo
```

描述模型标识、请求参数和连接层透传信息。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private static final String GREATER_THAN_ZERO_MESSAGE =` | 保存 `GREATER_THAN_ZERO_MESSAGE` 相关状态或配置。 |
| `private String apiKey =` | 保存 `apiKey` 相关状态或配置。 |
| `private String apiBase` | 保存 `apiBase` 相关状态或配置。 |
| `private String modelName =` | 保存 `modelName` 相关状态或配置。 |
| `private Double temperature = 0.95` | 保存 `temperature` 相关状态或配置。 |
| `private Double topP = 0.1` | 保存 `topP` 相关状态或配置。 |
| `private boolean streaming = false` | 保存 `streaming` 相关状态或配置。 |
| `private int timeout = 60` | 保存 `timeout` 相关状态或配置。 |
| `private ModelHttpVersion httpVersion` | 保存底层 HTTP 客户端版本偏好，可透传到 `ModelClientConfig`。 |
| `private boolean verifySsl = true` | 保存 `verifySsl` 相关状态或配置。 |
| `private String sslCert` | 保存 `sslCert` 相关状态或配置。 |
| `private Map<String, String> headers = new LinkedHashMap<>()` | 保存附加 HTTP 请求头配置。 |
| `private Map<String, Object> extraFields = new HashMap<>()` | 保存额外扩展字段。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseModelInfo(String apiKey, String apiBase, String modelName, Double temperature, Double topP, Boolean streaming, Integer timeout, ModelHttpVersion httpVersion, Boolean verifySsl, String sslCert, Map<String, String> headers, Map<String, Object> extraFields) {` | 构造 `BaseModelInfo` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> getExtraFields() {` | 返回 `extraFields` 属性。 |
| `public void setExtraField(String key, Object value) {` | 设置 `extraField` 属性。 |
| `public void setTimeout(int timeout) {` | 设置 `timeout` 属性。 |
| `public void setExtraFields(Map<String, Object> extraFields) {` | 设置 `extraFields` 属性。 |
| `public Map<String, String> getHeaders() {` | 返回附加请求头集合。 |
| `public void setHeaders(Map<String, String> headers) {` | 设置附加请求头集合。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `httpVersion` 会在 `LlmEventHandler`、`WorkflowEventHandler` 等高层入口组装 `ModelClientConfig` 时继续透传。
