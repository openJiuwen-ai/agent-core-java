# com.openjiuwen.core.common.utils.HashUtil

## class HashUtil

```java
public final class HashUtil
```

`HashUtil` generates deterministic SHA-256 cache keys from API credentials and provider metadata.

## Constructors

| Signature | Description |
| --- | --- |
| `private HashUtil()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String generateKey(String apiKey, String apiBase, String modelProvider)` | Sort the three input parts, concatenate them, and return a hex-encoded SHA-256 digest. |
| `public static String generateKey(String apiKey, String apiBase)` | Convenience overload that defaults `modelProvider` to `"openai"`. |

## Notes

- Sorting the inputs makes the generated key independent of the original argument ordering.
- The implementation throws a `RuntimeException` only when the JRE cannot supply `SHA-256`.
