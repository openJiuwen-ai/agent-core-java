# com.openjiuwen.core.common.security.UserConfig

## class UserConfig

```java
public final class UserConfig
```

`UserConfig` loads and caches the security-related runtime properties used by the common security helpers.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `DEFAULT_SENSITIVE_PATHS` | `List<String>` | built-in list | Default Unix and Windows sensitive prefixes used when no configuration file supplies `settings.sensitive_paths`. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void setConfigPath(Path path)` | Register the properties / ini file that should be loaded by the singleton before first access. |
| `public static UserConfig getConfig()` | Return the shared singleton instance, lazily loading properties from the configured file path. |
| `public static boolean isSensitive()` | Return whether sensitive-mode logging / masking is enabled, with `IS_SENSITIVE=false` forcing a `false` result. |
| `public static List<String> getSensitivePaths()` | Return the resolved sensitive-path list from the singleton instance. |
| `public static void setSensitive(boolean isSensitive)` | Override the in-memory `sensitive` flag after the singleton has been created. |
| `public List<String> getSensitivePathsList()` | Lazily parse `settings.sensitive_paths` as a comma-separated list, or fall back to `DEFAULT_SENSITIVE_PATHS`. |
| `public static synchronized void reset()` | Clear the singleton and configured path, primarily for tests. |

## Notes

- `setConfigPath` must be called before `getConfig()`; otherwise later attempts to change the config path fail with `IllegalStateException`.
- The file-backed `settings.is_sensitive` property defaults to `true` when it is missing.
