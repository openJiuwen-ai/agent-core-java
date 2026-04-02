# com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser

## 类 WordParser

```java
public class WordParser extends Parser
```

`WordParser` 使用 Apache POI 解析 DOCX，支持段落、表格以及可选图片 caption。

## 公开方法

- `parse(...)`：解析失败或结果为空时返回空列表。
- `supports(String doc)`：仅支持 `.docx`。

## 解析流程

- 遍历 `XWPFDocument.getBodyElements()`。
- 段落使用 `paragraph.getText()`。
- 表格会按行转为制表符分隔文本。
- 提供 `llmClient` 时，会把文档中的图片导出到本地，再通过 `ImageCaptioner` 生成说明并追加到结果文本末尾。
