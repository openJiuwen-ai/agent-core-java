# com.openjiuwen.core.common.security.JsonUtils

## class JsonUtils

```java
public final class JsonUtils
```

`JsonUtils` wraps Jackson JSON parsing and serialization so callers can choose between openJiuwen status-code exceptions and logged fallback values.

## Constructors

| Signature | Description |
| --- | --- |
| `private JsonUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static <T> T safeJsonLoads(String json, Class<T> type, T defaultValue)` | Parse `json` into `type`; if parsing fails and `defaultValue` is non-null, log the error and return the fallback instead of throwing. |
| `public static <T> T safeJsonLoads(String json, Class<T> type)` | Parse JSON and throw `COMMON_JSON_INPUT_PROCESS_ERROR` when decoding fails. |
| `public static String safeJsonDumps(Object obj, String defaultValue)` | Serialize `obj`; if serialization fails and `defaultValue` is non-null, log the error and return the fallback. |
| `public static String safeJsonDumps(Object obj)` | Serialize `obj` and throw `COMMON_JSON_EXECUTION_PROCESS_ERROR` when serialization fails. |
| `public static ObjectMapper getMapper()` | Return the shared process-wide `ObjectMapper` instance used by the helper methods. |

## Notes

- Parsing failures are mapped to `StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR`.
- Serialization failures are mapped to `StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR`.
- The class keeps a single static `ObjectMapper` and SLF4J logger for all callers.
