# com.openjiuwen.core.common.security.PathChecker

## class PathChecker

```java
public final class PathChecker
```

`PathChecker` is a lazily initialized singleton that decides whether a filesystem path falls under one of the configured sensitive prefixes.

## Methods

| Signature | Description |
| --- | --- |
| `public static PathChecker getInstance()` | Return the shared singleton, creating it with double-checked locking on first use. |
| `public boolean checkSensitive(String path)` | Normalize `path` and return `true` when it starts with any configured sensitive prefix. |
| `public static boolean isSensitivePath(String path)` | Convenience wrapper around the singleton `checkSensitive` call. |

## Notes

- Startup configuration is loaded once through `UserConfig.getSensitivePaths()`; if that lookup fails, `UserConfig.DEFAULT_SENSITIVE_PATHS` is used instead.
- Invalid or unparseable input paths fail closed: `checkSensitive` returns `true` when normalization throws.
