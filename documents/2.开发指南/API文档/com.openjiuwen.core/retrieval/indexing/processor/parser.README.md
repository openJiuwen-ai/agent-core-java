# parser

`com.openjiuwen.core.retrieval.indexing.processor.parser` 负责把本地文件或网页链接转换为 `Document`。该包覆盖文本、表格、图片、网页与自动路由入口。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`AutoFileParser`](./parser/AutoFileParser.md) | `class` | 按文件后缀选择具体解析器。 |
| [`AutoLinkParser`](./parser/AutoLinkParser.md) | `class` | 按 URL 规则在网页和微信公众号文章解析器间路由。 |
| [`AutoParser`](./parser/AutoParser.md) | `class` | 统一入口，先尝试 link parser，再尝试 file parser。 |
| [`ExcelParser`](./parser/ExcelParser.md) | `class` | 解析 xlsx/csv/tsv，并输出行级与列级文档。 |
| [`ImageCaptioner`](./parser/ImageCaptioner.md) | `class` | 调用 LLM 为图片生成简短描述。 |
| [`ImageParser`](./parser/ImageParser.md) | `class` | 通过 `ImageCaptioner` 把图片转成文本。 |
| [`JsonParser`](./parser/JsonParser.md) | `class` | 读取 JSON 文件并尽量格式化。 |
| [`Parser`](./parser/Parser.md) | `abstract class` | `String -> List<Document>` 的解析抽象。 |
| [`PDFParser`](./parser/PDFParser.md) | `class` | 解析 PDF 文本，并可选为内嵌图片追加 caption。 |
| [`TextFileParser`](./parser/TextFileParser.md) | `class` | 简单 UTF-8 文本文件解析器。 |
| [`TxtMdParser`](./parser/TxtMdParser.md) | `class` | 读取 `.txt` / `.md` / `.markdown` 文件。 |
| [`WebPageParser`](./parser/WebPageParser.md) | `class` | 通用网页解析器。 |
| [`WeChatArticleParser`](./parser/WeChatArticleParser.md) | `class` | 微信公众号文章专用解析器。 |
| [`WordParser`](./parser/WordParser.md) | `class` | 解析 DOCX 段落、表格，并可选补充图片 caption。 |

## 关键行为

- `AutoFileParserTest` 验证文件路由器只接受已存在且已注册后缀的文件，并为解析结果补入 `file_ext`、`title`、`file_path` 等 metadata。
- `AutoLinkParserTest` 验证微信文章 URL 优先命中 `WeChatArticleParser`，其余 HTTP/HTTPS 链接走 `WebPageParser`。
- `AutoParserTest` 验证统一入口会优先尝试链接解析，再尝试文件解析。
- `PDFParser`、`WordParser`、`ImageParser` 在提供 `BaseModelClient` 时会复用 `ImageCaptioner` 为图片内容补充文字描述。
