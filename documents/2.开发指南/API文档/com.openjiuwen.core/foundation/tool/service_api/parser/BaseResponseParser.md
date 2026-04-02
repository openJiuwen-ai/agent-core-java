# com.openjiuwen.core.foundation.tool.service_api.parser.BaseResponseParser

## class BaseResponseParser

```java
public abstract class BaseResponseParser
```

Base class for response parsers.

## Notes

- `decodeBytes(...)` respects a charset declared in `Content-Type` and falls back to UTF-8 when no supported charset is present.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract boolean canParse(String contentType, int statusCode, java.util.Map<String, String> headers)` | Check if this parser can handle the response. |
| `public abstract Object parse(byte[] responseData, String contentType)` | Parse the response data. |
| `protected String decodeBytes(byte[] data, String contentType)` | Decode bytes using the charset from Content-Type, defaulting to UTF-8. |
| `protected static String extractCharsetFromContentType(String contentType)` | Extract charset from Content-Type header value (e.g., "text/html; charset=utf-8"). |

## Related Tests

- `ResponseParserTest`
