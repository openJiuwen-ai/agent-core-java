# com.openjiuwen.core.retrieval.common.MultimodalDocument

## 类 MultimodalDocument

```java
public class MultimodalDocument extends Document
```

多模态检索文档模型。它继承 retrieval `Document`，允许在同一个文档中按顺序追加 `text`、`image`、`audio`、`video` 字段，并生成通用 content 结构或 DashScope 输入结构。当前 Java 实现以 `addField` 方法维护内部字段列表，返回 `this` 以支持链式调用。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MultimodalDocument()` | 创建空文本、多模态字段为空的文档，`id_` 自动生成。 |
| `public MultimodalDocument(String id)` | 创建指定 `id_` 的多模态文档，文本默认为空字符串。 |
| `public MultimodalDocument(Map<String, Object> metadata)` | 创建带 metadata 的多模态文档。 |
| `public MultimodalDocument(String id, String text, Map<String, Object> metadata)` | 创建完整多模态文档；`text == null` 时按空字符串处理。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Map<String, Object>> getContent()` | 返回按字段追加顺序生成的结构化内容列表，并返回缓存内容的深拷贝。 |
| `public Map<String, Object> getDashscopeInput()` | 返回 DashScope 输入字典的深拷贝；音频、base64 视频以及重复文本/视频会触发校验异常，多张图片会合并为 `multi_images`。 |
| `public MultimodalDocument addField(String kind)` | 无数据源时进入校验并抛出 `ValidationError`。 |
| `public MultimodalDocument addField(String kind, String data)` | 追加字符串数据源；`text` 直接作为原文，`image`/`video` 支持 URL 或 `data:{kind}/...;base64,...`，`audio` 只接受 `data:audio/...;base64,...`。 |
| `public MultimodalDocument addField(String kind, Path filePath)` | 从本地文件追加字段；文本按 UTF-8 读取，非文本转为 data URL。 |
| `public MultimodalDocument addField(String kind, String data, Path filePath, String dataId)` | 追加带显式数据源和可选 `dataId` 的字段；`dataId` 为 `null` 或空白时按未提供处理。 |
| `public MultimodalDocument addField(String kind, Object data, Object filePath, Object dataId)` | 通用入口，用于保留动态校验边界。 |
| `public MultimodalDocument strip()` | 内部多模态字段为空时返回 `null`，否则返回当前实例。 |

## content 输出

| kind | 输出结构 |
| --- | --- |
| `text` | `{"type": "text", "text": data}` |
| `image` | `{"type": "image_url", "image_url": {"url": data}}` |
| `video` | `{"type": "video_url", "video_url": {"url": data}}` |
| `audio` | `{"type": "input_audio", "input_audio": {"data": data, "format": format}}` |

非文本字段未显式提供 `dataId` 时会生成 32 位十六进制 UUID，并在 content 项中写入 `uuid`；文本字段默认不带 `uuid`。

## DashScope 输入

- 单张图片写入 `image`，多张图片写入 `multi_images`。
- 文本写入 `text`，视频 URL 写入 `video`。
- `audio` 不支持 DashScope 输入，会抛出 `unsupported_format`。
- `data:video/...;base64,...` 不支持 DashScope 输入，会抛出 `unsupported_format`。
- 文本或视频重复出现时会抛出 `multiple_<kind>_fields_present`。

## 校验规则

- `kind` 只支持 `text`、`image`、`audio`、`video`。
- 每次追加必须且只能提供 `data` 或 `filePath` 之一。
- 字符串 `data` 中，`text` 不做 URL/data URL 格式校验；`image` 和 `video` 可用 URL 或 data URL；`audio` 不接受普通 URL。
- `filePath` 必须是存在的普通文件 `Path`。
- `dataId` 为 `null` 或空白时视为未提供；显式非空 `dataId` 必须是长度不超过 32 的字符串。
- 本地 `.jfif` 图片按 `image/jpeg` 处理；常见 png/jpg/wav/mp4 在系统 MIME 探测失败时有兜底映射。
