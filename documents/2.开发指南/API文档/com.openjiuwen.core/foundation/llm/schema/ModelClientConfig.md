# com.openjiuwen.core.foundation.llm.schema.ModelClientConfig

## class ModelClientConfig

```java
public class ModelClientConfig
```

Java API page for `ModelClientConfig`.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `clientId` | `String` | Stored `clientId` value. |
| `clientProvider` | `String` | Stored `clientProvider` value. |
| `apiKey` | `String` | Stored `apiKey` value. |
| `apiBase` | `String` | Stored `apiBase` value. |
| `timeout` | `double` | Stored `timeout` value. |
| `maxRetries` | `int` | Stored `maxRetries` value. |
| `verifySsl` | `boolean` | Stored `verifySsl` value. |
| `sslCert` | `String` | Stored `sslCert` value. |
| `extraFields` | `Map<String, Object>` | Stored `extraFields` value. |

## Nested Types

| Declaration | Description |
| --- | --- |
| `public static class Builder` | Nested public type declared on this page. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getClientId()` | Return the `clientId` value. |
| `public String getClientProvider()` | Return the `clientProvider` value. |
| `public String getApiKey()` | Return the `apiKey` value. |
| `public String getApiBase()` | Return the `apiBase` value. |
| `public double getTimeout()` | Return the `timeout` value. |
| `public int getMaxRetries()` | Return the `maxRetries` value. |
| `public boolean isVerifySsl()` | Return whether `verifySsl` is enabled. |
| `public String getSslCert()` | Return the `sslCert` value. |
| `public Map<String, Object> getExtraFields()` | Return the `extraFields` value. |
| `public static Builder builder()` | Create a builder for this type. |
| `public String toString()` | Execute `toString`. |

## Notes

- `ModelClientConfigTest` covers defaults for timeout, retries, SSL verification, generated client IDs, and extra fields.
