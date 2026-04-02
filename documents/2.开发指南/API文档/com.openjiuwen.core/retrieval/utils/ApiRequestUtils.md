# com.openjiuwen.core.retrieval.utils.ApiRequestUtils

## class ApiRequestUtils

```java
public final class ApiRequestUtils
```

Shared HTTP request helper for retrieval services with retry support.

## Constructors

| Signature | Description |
| --- | --- |
| `private ApiRequestUtils()` | Create a new `ApiRequestUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName)` | Send a POST request with JSON payload and retry support (sync). |
| `public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName, StatusCodeCallback callback)` | Send a POST request with JSON payload, retry support, and pluggable status-code callback (sync). |
| `HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())` | Execute `send`. |
| `public static CompletableFuture<JsonNode> postJsonWithRetryAsync(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName, StatusCodeCallback callback)` | Send a POST request with JSON payload asynchronously with retry and pluggable callback. |

## Nested Types

| Type | Kind | Description |
| --- | --- | --- |
| `StatusCodeCallback` | `interface` | Callback for custom status code handling. |
