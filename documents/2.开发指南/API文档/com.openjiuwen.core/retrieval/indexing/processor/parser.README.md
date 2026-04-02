# parser

`com.openjiuwen.core.retrieval.indexing.processor.parser` contains parsers for files, web pages, spreadsheets, images, PDFs, and other raw content sources.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`AutoFileParser`](./parser/AutoFileParser.md) | `class` | File parser router based on file extension. |
| [`AutoLinkParser`](./parser/AutoLinkParser.md) | `class` | URL parser router. |
| [`AutoParser`](./parser/AutoParser.md) | `class` | Top-level parser that routes between file and URL parsers. |
| [`ExcelParser`](./parser/ExcelParser.md) | `class` | Parser for xlsx/csv/tsv tabular files that emits row and column documents. |
| [`ImageCaptioner`](./parser/ImageCaptioner.md) | `class` | Lightweight image caption helper aligned with the Python retrieval parser stack. |
| [`ImageParser`](./parser/ImageParser.md) | `class` | Parser for image files using LLM captions. |
| [`JsonParser`](./parser/JsonParser.md) | `class` | JSON file parser that returns formatted JSON text when possible. |
| [`PDFParser`](./parser/PDFParser.md) | `class` | PDF parser with optional image caption extraction. |
| [`Parser`](./parser/Parser.md) | `class` | Document parser abstraction. |
| [`TextFileParser`](./parser/TextFileParser.md) | `class` | Simple UTF-8 text file parser. |
| [`TxtMdParser`](./parser/TxtMdParser.md) | `class` | TXT/MD file parser aligned with the Python TxtMdParser behavior. |
| [`WeChatArticleParser`](./parser/WeChatArticleParser.md) | `class` | WeChat article parser. |
| [`WebPageParser`](./parser/WebPageParser.md) | `class` | Generic web page parser. |
| [`WordParser`](./parser/WordParser.md) | `class` | DOCX parser with optional image caption support. |

## Notes

- The current page also links the 14 direct public type page(s) defined in this package.
