# com.openjiuwen.core.foundation.llm.schema.ModelClientConfig

## 类 ModelClientConfig

```java
public class ModelClientConfig
```

描述 provider、clientId、apiBase、apiKey、HTTP 版本等客户端连接配置。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final String clientId` | 保存 `clientId` 标识。 |
| `private final String clientProvider` | 保存 `clientProvider` 相关状态或配置。 |
| `private final String apiKey` | 保存 `apiKey` 相关状态或配置。 |
| `private final String apiBase` | 保存 `apiBase` 相关状态或配置。 |
| `private final double timeout` | 保存 `timeout` 相关状态或配置。 |
| `private final ModelHttpVersion httpVersion` | 保存底层 HTTP 客户端版本偏好；未设置时保持 JDK 默认协商行为。 |
| `private final int maxRetries` | 保存 `maxRetries` 相关状态或配置。 |
| `private final boolean verifySsl` | 保存 `verifySsl` 相关状态或配置。 |
| `private final String sslCert` | 保存 `sslCert` 相关状态或配置。 |
| `private final Map<String, String> headers` | 保存附加 HTTP 请求头配置。 |
| `private final Map<String, Object> extraFields` | 保存 `extraFields` 相关状态或配置。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public static class Builder {` | 提供链式构建当前配置对象的辅助类型。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getClientId() {` | 返回 `clientId` 属性。 |
| `public String getClientProvider() {` | 返回 `clientProvider` 属性。 |
| `public String getApiKey() {` | 返回 `apiKey` 属性。 |
| `public String getApiBase() {` | 返回 `apiBase` 属性。 |
| `public double getTimeout() {` | 返回 `timeout` 属性。 |
| `public ModelHttpVersion getHttpVersion() {` | 返回显式配置的 HTTP 版本；可选值见 `ModelHttpVersion`。 |
| `public int getMaxRetries() {` | 返回 `maxRetries` 属性。 |
| `public boolean isVerifySsl() {` | 返回 `verifySsl` 的布尔状态。 |
| `public String getSslCert() {` | 返回 `sslCert` 属性。 |
| `public Map<String, String> getHeaders() {` | 返回附加请求头集合。 |
| `public Map<String, Object> getExtraFields() {` | 返回 `extraFields` 属性。 |
| `public static Builder builder() {` | 返回 Builder 入口以便链式构建。 |
| `public String toString() {` | 执行 `toString` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `ModelClientConfigTest` 覆盖 builder 链式设置与 getter 行为。
- `httpVersion` 使用枚举 `ModelHttpVersion`，当前支持 `HTTP_1_1` 和 `HTTP_2`。
- JSON 反序列化兼容 `HTTP_1_1`、`HTTP/1.1`、`1.1`、`HTTP_2`、`HTTP/2`、`2`、`2.0` 等写法。
