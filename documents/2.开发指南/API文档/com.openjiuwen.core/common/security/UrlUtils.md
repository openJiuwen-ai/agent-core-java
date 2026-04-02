# com.openjiuwen.core.common.security.UrlUtils

## class UrlUtils

```java
public final class UrlUtils
```

`UrlUtils` validates outbound URLs, blocks private-address targets by default, and resolves proxy settings from the standard process environment.

## Constructors

| Signature | Description |
| --- | --- |
| `private UrlUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void checkUrlIsValid(String url)` | Require a non-empty `http://` or `https://` URL, resolve the hostname, and reject loopback/site-local/link-local/any-local targets unless SSRF protection is explicitly disabled. |
| `public static String getGlobalProxyUrl(String url)` | Return the configured proxy URL from `http_proxy` / `https_proxy` / uppercase variants, unless `url` matches the NO_PROXY rules. |
| `public static Map<String, String> getGlobalProxies(String url)` | Return a two-entry `http` / `https` proxy map when a global proxy applies, or `null` when proxying is bypassed. |
| `public static boolean shouldBypassProxy(String url)` | Evaluate the URL hostname against the combined `NO_PROXY` / `no_proxy` allowlist. |

## Notes

- Placeholder segments like `{tenant}` are sanitized before URI parsing so templated URLs can still be validated.
- `NO_PROXY` entries support exact hostnames, leading-dot suffix matches, wildcard `*`, direct IP matches, and CIDR ranges.
- Setting env var or system property `SSRF_PROTECT_ENABLED=false` disables the internal-IP rejection path.
