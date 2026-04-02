# com.openjiuwen.core.retrieval.utils.ApiRequestUtils

## 类 ApiRequestUtils

```java
public final class ApiRequestUtils
```

发送带重试逻辑的 HTTP JSON POST 请求，并将失败统一转换为 retrieval 模块异常。

## 嵌套类型

| 类型 | 说明 |
| --- | --- |
| `StatusCodeCallback` | 状态码回调接口，决定某次失败是否继续重试。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName)` | 使用默认回调发送同步 POST 请求。 |
| `public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName, StatusCodeCallback callback)` | 使用自定义回调发送同步 POST 请求。 |
| `public static CompletableFuture<JsonNode> postJsonWithRetryAsync(...)` | 异步发送默认回调请求。 |

## 说明

- 默认会在 `429`、`500`、`503` 时允许重试。
- `InterruptedException` 会恢复线程中断标记。
