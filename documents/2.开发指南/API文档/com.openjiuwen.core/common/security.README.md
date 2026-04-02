# security

`com.openjiuwen.core.common.security` contains low-level safety helpers for exception formatting, JSON parsing, URL and SSL validation, and user-configured sensitive-path checks.

## Core Types

| Type | Description |
| --- | --- |
| [`ExceptionUtils`](./security/ExceptionUtils.md) | Formats validation failures and unwraps nested exception chains. |
| [`JsonUtils`](./security/JsonUtils.md) | Wraps Jackson serialization/deserialization with openJiuwen status-code errors. |
| [`PathChecker`](./security/PathChecker.md) | Singleton that resolves whether a filesystem path falls under a sensitive prefix. |
| [`SslUtils`](./security/SslUtils.md) | Builds strict or insecure SSL contexts and derives verify/certificate settings from runtime configuration. |
| [`UrlUtils`](./security/UrlUtils.md) | Validates outbound URLs and resolves global proxy / NO_PROXY behavior. |
| [`UserConfig`](./security/UserConfig.md) | Loads and caches the sensitive-path configuration used by the security helpers. |

## Notes

- `PathChecker` and `UserConfig` form the shared path-sensitivity decision path for this package.
- Runtime behavior depends on environment variables or system properties such as `SAFE_CERT_DIR`, `IS_SENSITIVE`, `NO_PROXY`, `http_proxy`, `https_proxy`, and `SSRF_PROTECT_ENABLED`.
