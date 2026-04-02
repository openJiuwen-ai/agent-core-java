# com.openjiuwen.core.common.security.SslUtils

## class SslUtils

```java
public final class SslUtils
```

`SslUtils` builds strict or insecure SSL contexts and derives SSL verification settings from environment-driven runtime configuration.

## Constructors

| Signature | Description |
| --- | --- |
| `private SslUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static SSLContext createStrictSslContext(String sslCertPath)` | Create a TLS 1.2 context, optionally loading a CA certificate from `sslCertPath` after validating that the file stays inside `SAFE_CERT_DIR` and is at most 1 MiB. |
| `public static SSLContext createInsecureSslContext()` | Create a TLS 1.2 context backed by a trust manager that accepts every certificate. |
| `public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv, List<String> triggerValues, boolean urlIsHttps)` | Return a two-element array of `[sslVerify, sslCertPath]`, using env vars / system properties to decide whether verification is disabled and which certificate path to expose. |

## Notes

- `createStrictSslContext` throws `COMMON_SSL_CONTEXT_INIT_FAILED` when the certificate path escapes the allowed directory, has an invalid size, or cannot be loaded.
- `getSslConfig` treats non-HTTPS targets as `sslVerify = false` and does not read certificate settings for them.
- The verify-switch comparison lowercases the runtime value before checking `triggerValues`.
