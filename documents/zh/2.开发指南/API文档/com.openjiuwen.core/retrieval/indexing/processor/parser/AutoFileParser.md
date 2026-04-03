# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser

## 类 AutoFileParser

```java
public class AutoFileParser extends Parser
```

`AutoFileParser` 按文件后缀把本地文件路由到具体解析器，并在输出文档 metadata 中补充文件来源信息。

## 默认支持格式

- 文本：`.txt`、`.md`、`.markdown`
- 结构化：`.json`
- PDF：`.pdf`
- Office：`.docx`、`.xlsx`
- 表格文本：`.csv`、`.tsv`
- 图片：`.png`、`.jpg`、`.jpeg`、`.webp`、`.gif`、`.jfif`

## 公开静态方法

- `registerNewParser(String extension, Supplier<? extends Parser> supplier)`：注册新后缀。
- `getSupportedFormats()`：返回去重排序后的后缀列表。

## 公开方法

### `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)`

- 文件不存在时抛出 `RETRIEVAL_INDEXING_FILE_NOT_FOUND`。
- 未注册后缀时抛出 `validation("Unsupported format: ...")`。
- 解析成功后会补入 `doc_id`、`title`、`file_path`、`file_ext` metadata。

### `public boolean supports(String doc)`

只有文件存在且后缀已注册时才返回 `true`。

## 相关测试

- `AutoFileParserTest` 验证 `.json` 文件会携带 `file_ext` metadata，并覆盖缺失文件和不支持后缀的异常路径。
