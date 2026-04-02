# com.openjiuwen.core.foundation.tool.service_api.parser.ParserRegistry

## class ParserRegistry

```java
public final class ParserRegistry
```

Registry for response parsers and decompressors (singleton).

## Notes

- The singleton registers `JsonResponseParser`, `TextResponseParser`, `GzipDecompressor`, and `DeflateDecompressor` by default.

## Constructors

| Signature | Description |
| --- | --- |
| `private ParserRegistry()` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public static ParserRegistry getInstance()` | Get the singleton instance. */ |
| `public void register(BaseResponseParser parser)` | Register a response parser. */ |
| `public void registerDecompressor(String encoding, BaseResponseDecompressor decompressor)` | Register a decompressor for the given encoding. */ |
| `public Object parse(Map<String, String> responseHeaders, byte[] responseData, int statusCode)` | Parse the HTTP response by decompressing (if needed) and then delegating to a matching parser. |

## Related Tests

- `ResponseParserTest`
