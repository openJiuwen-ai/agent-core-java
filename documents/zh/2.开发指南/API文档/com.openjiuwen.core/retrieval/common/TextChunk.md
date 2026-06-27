# com.openjiuwen.core.retrieval.common.TextChunk

## 类 TextChunk

```java
public class TextChunk
```

检索文档分块模型，保存分块标识、分块文本、父文档标识、元数据以及可选 embedding。该类是普通 Java POJO，序列化字段保留 Python 对应名称 `id_` 和 `doc_id`。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` / `id_` | `String` | 分块标识，构造时要求非 `null`；Jackson 序列化字段名为 `id_`。 |
| `text` | `String` | 分块文本，构造时要求非 `null`。 |
| `docId` / `doc_id` | `String` | 父文档标识，构造时要求非 `null`；Jackson 序列化字段名为 `doc_id`。 |
| `metadata` | `Map<String, Object>` | 分块元数据。构造和 setter 入参为 `null` 时会变为空 `LinkedHashMap`，非 `null` 入参会被复制，getter 返回副本。 |
| `embedding` | `List<Double>` | 可选向量。构造、setter 和 getter 都会做列表副本，避免调用方直接修改内部列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TextChunk()` | 不创建空分块；会抛出 `ValidationError`，错误类型为 `missing_required_fields`。 |
| `public TextChunk(String id, String text, String docId)` | 创建分块，metadata 为空，embedding 为 `null`。 |
| `public TextChunk(String id, String text, String docId, Map<String, Object> metadata)` | 创建带 metadata 的分块。 |
| `public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Double> embedding)` | 创建完整分块；`id`、`text`、`docId` 任一为 `null` 时抛出 `ValidationError`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static TextChunk fromDocument(Document doc, String chunkText)` | 从文档创建分块，并自动生成分块 UUID。 |
| `public static TextChunk fromDocument(Document doc, String chunkText, String id)` | 从文档创建分块；`id` 为 `null` 或空白时自动生成 UUID。 |
| `public String getId_()` / `public void setId_(String id)` | 访问或设置分块标识。 |
| `public String getText()` / `public void setText(String text)` | 访问或设置分块文本。 |
| `public String getDocId()` / `public void setDocId(String docId)` | 访问或设置父文档标识的 Java camelCase 入口。 |
| `public String getDoc_id()` / `public void setDoc_id(String docId)` | 提供与 `doc_id` 对应的 Java 访问器。 |
| `public Map<String, Object> getMetadata()` / `public void setMetadata(Map<String, Object> metadata)` | 访问或设置分块元数据；getter 和 setter 都会与内部 Map 做拷贝隔离。 |
| `public List<Double> getEmbedding()` / `public void setEmbedding(List<Double> embedding)` | 访问或设置 embedding；非 `null` 列表会被复制。 |

## 说明

- `fromDocument` 会复制源文档 metadata 的当前内容，并把源文档 `id_` 写入 `docId`。
- `metadata` 保持动态字典边界，因此 Java 类型为 `Map<String, Object>`。
- `embedding` 不是不可变列表对象；实现通过防御性拷贝隔离内部状态。
- 构造器只校验 `id`、`text`、`docId` 是否为 `null`，不额外拒绝空字符串；普通 setter 不重新执行构造时的必填校验。
