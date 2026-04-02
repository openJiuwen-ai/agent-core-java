# com.openjiuwen.core.retrieval.common.Document

## 类 Document

```java
public class Document
```

原始文档模型，保存文档标识、正文与元数据。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `String` | 文档标识；未显式提供时会生成 UUID。 |
| `text` | `String` | 文档正文，不能为空。 |
| `metadata` | `Map<String, Object>` | 文档元数据，内部会复制保存。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Document()` | 创建空对象。 |
| `public Document(String text)` | 使用自动生成的 `id` 创建文档。 |
| `public Document(String id, String text)` | 使用指定 `id` 创建文档。 |
| `public Document(String id, String text, Map<String, Object> metadata)` | 创建完整文档对象。 |

## 说明

- `text` 不能为空；测试确认传入空文本会抛出异常。
- `metadata` 为 `null` 时会退回为空 `Map`。
