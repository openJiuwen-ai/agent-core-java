# com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser

## 类 PDFParser

```java
public class PDFParser extends Parser
```

`PDFParser` 使用 PDFBox 提取 PDF 文本，并在提供 `BaseModelClient` 时，为嵌入图片追加 caption 文本。

## 公开方法

- `parse(...)`：解析失败或内容为空时返回空列表。
- `supports(String doc)`：仅支持 `.pdf`。

## 解析流程

- 通过 `PDFTextStripper` 提取正文。
- 深度遍历 `PDResources` 中的图片和表单对象，保存图片到 `ImageCaptioner.SAVED_IMAGE_DIR`。
- 提供 `llmClient` 时，对提取到的图片调用 `ImageCaptioner.captionImages(...)`。
