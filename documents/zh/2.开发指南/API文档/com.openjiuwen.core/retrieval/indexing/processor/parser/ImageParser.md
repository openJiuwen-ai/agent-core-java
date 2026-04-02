# com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser

## 类 ImageParser

```java
public class ImageParser extends Parser
```

`ImageParser` 把图片内容转换成文本说明。它会先复制图片，再调用 `ImageCaptioner` 生成 caption，并把非空 caption 合并为单个 `Document` 文本。

## 公开方法

- `parse(...)`：caption 为空或发生异常时返回空列表。
- `supports(String doc)`：支持 `.png`、`.jpg`、`.jpeg`、`.webp`、`.gif`、`.jfif`。

## 说明

- 如果未提供可用的 `BaseModelClient`，`parseContent(...)` 通常会返回 `null`，因此不会产出文档。
