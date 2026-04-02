# com.openjiuwen.core.retrieval.common.TextChunk

## 类 TextChunk

```java
public class TextChunk
```

文档分块模型，保存分块标识、文本、来源文档标识、元数据与可选 embedding。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static TextChunk fromDocument(Document document, String text)` | 根据文档创建分块并自动生成分块标识。 |
| `public static TextChunk fromDocument(Document document, String text, String id)` | 根据文档与显式标识创建分块。 |

## 说明

- `id`、`docId` 不能为空白，`text` 不能为空。
- `embedding` 会以不可变列表形式保存。
