# com.openjiuwen.core.foundation.llm.schema.ModelClientConfig

## 类 ModelClientConfig

```java
public class ModelClientConfig
```

描述 provider、clientId、apiBase、apiKey 等客户端连接配置。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final String clientId` | 保存 `clientId` 标识。 |
| `private final String clientProvider` | 保存 `clientProvider` 相关状态或配置。 |
| `private final String apiKey` | 保存 `apiKey` 相关状态或配置。 |
| `private final String apiBase` | 保存 `apiBase` 相关状态或配置。 |
| `private final double timeout` | 保存 `timeout` 相关状态或配置。 |
| `private final int maxRetries` | 保存 `maxRetries` 相关状态或配置。 |
| `private final boolean verifySsl` | 保存 `verifySsl` 相关状态或配置。 |
| `private final String sslCert` | 保存 `sslCert` 相关状态或配置。 |
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
| `public int getMaxRetries() {` | 返回 `maxRetries` 属性。 |
| `public boolean isVerifySsl() {` | 返回 `verifySsl` 的布尔状态。 |
| `public String getSslCert() {` | 返回 `sslCert` 属性。 |
| `public Map<String, Object> getExtraFields() {` | 返回 `extraFields` 属性。 |
| `public static Builder builder() {` | 返回 Builder 入口以便链式构建。 |
| `public String toString() {` | 执行 `toString` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `ModelClientConfigTest` 覆盖 builder 链式设置与 getter 行为。
