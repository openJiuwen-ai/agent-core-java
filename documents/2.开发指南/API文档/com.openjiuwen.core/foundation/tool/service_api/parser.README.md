# parser

`com.openjiuwen.core.foundation.tool.service_api.parser` contains response parser and decompressor components used by REST-backed tools.

## Core Types

| Type | Description |
| --- | --- |
| [`BaseResponseDecompressor`](parser/BaseResponseDecompressor.md) | Base class for response decompressors. |
| [`BaseResponseParser`](parser/BaseResponseParser.md) | Base class for response parsers. |
| [`DeflateDecompressor`](parser/DeflateDecompressor.md) | Deflate decompressor. |
| [`GzipDecompressor`](parser/GzipDecompressor.md) | GZIP decompressor. |
| [`JsonResponseParser`](parser/JsonResponseParser.md) | JSON response parser. |
| [`ParserRegistry`](parser/ParserRegistry.md) | Registry for response parsers and decompressors (singleton). |
| [`TextResponseParser`](parser/TextResponseParser.md) | Text response parser. |

## Notes

- `ResponseParserTest` covers parser selection, gzip/deflate decompression, charset handling, and singleton registry behavior.
