# parser

`com.openjiuwen.core.foundation.tool.service_api.parser` 提供响应体解压缩器、内容类型解析器，以及统一的注册中心。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseResponseDecompressor`](parser/BaseResponseDecompressor.md) | 解压缩器抽象基类。 |
| [`BaseResponseParser`](parser/BaseResponseParser.md) | 响应解析器抽象基类。 |
| [`DeflateDecompressor`](parser/DeflateDecompressor.md) | `deflate` 解压缩实现。 |
| [`GzipDecompressor`](parser/GzipDecompressor.md) | `gzip` 与 `x-gzip` 解压缩实现。 |
| [`JsonResponseParser`](parser/JsonResponseParser.md) | JSON 内容解析器。 |
| [`ParserRegistry`](parser/ParserRegistry.md) | 统一注册和调度解析器与解压缩器。 |
| [`TextResponseParser`](parser/TextResponseParser.md) | 文本类内容解析器。 |

## 关键行为

- `ParserRegistry` 默认先注册 `JsonResponseParser`，再注册 `TextResponseParser`，因此优先命中 JSON 解析。
- `ParserRegistry` 默认注册 `gzip` 与 `deflate` 两种解压缩器。
- 当没有匹配的解析器时，`ParserRegistry.parse(...)` 会抛出 `IllegalArgumentException`。

## 相关测试

- `ResponseParserTest`
