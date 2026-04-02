# com.openjiuwen.core.retrieval.common.MultimodalDocument

## 类 MultimodalDocument

```java
public class MultimodalDocument extends Document
```

多模态文档模型，支持向单个文档中追加文本、图片、音频、视频字段，并生成模型请求可直接消费的内容结构。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Map<String, Object>> getContent()` | 返回适配模型请求的结构化内容。 |
| `public void addField(String kind, String data)` | 追加字符串形式的数据字段。 |
| `public void addField(String kind, Object data, Object filePath, Object dataId)` | 追加带 `data` 或 `filePath` 的字段。 |
| `public void addField(String kind, Path filePath)` | 从本地文件追加字段。 |

## 说明

- 仅支持 `text`、`image`、`audio`、`video` 四类 `kind`。
- 每次追加时必须且只能提供 `data` 或 `filePath` 其中一种来源。
- 非文本文件会转换为 data URL；测试覆盖了文本文件、图片文件与显式 data URL 的行为。
