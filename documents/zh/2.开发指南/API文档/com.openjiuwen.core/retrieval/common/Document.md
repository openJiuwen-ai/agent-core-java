# com.openjiuwen.core.retrieval.common.Document

## 类 Document

```java
public class Document extends com.openjiuwen.core.foundation.store.base_reranker.Document
```

检索模块对文档模型的公开入口。该类复用 foundation store 中的基础文档存储字段，同时在 retrieval 包下提供 `id_` 风格访问器和与当前 Java 翻译一致的必填文本校验。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` / `id_` | `String` | 文档标识。构造时未提供或为空白时沿用基础模型自动生成的 UUID。Jackson 序列化字段名仍为 `id_`。 |
| `text` | `String` | 文档正文。retrieval `Document` 构造时只要求非 `null`，允许空字符串。 |
| `metadata` | `Map<String, Object>` | 文档元数据。构造入参为 `null` 时会变为空 `LinkedHashMap`，非 `null` 入参会在构造时复制为新的 `LinkedHashMap`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Document()` | 不创建空文档；会抛出 `ValidationError`，错误类型为 `missing_text`。 |
| `public Document(String text)` | 使用自动生成的 `id_` 和空 metadata 创建文档。 |
| `public Document(String id, String text)` | 使用指定 `id_` 与空 metadata 创建文档；`id` 为 `null` 或空白时自动生成。 |
| `public Document(String id, String text, Map<String, Object> metadata)` | 创建完整文档对象；`text == null` 时抛出 `ValidationError`。 |

## 访问器

| 签名 | 说明 |
| --- | --- |
| `public String getId_()` | 返回基础模型中的 `id`。 |
| `public void setId_(String id)` | 设置基础模型中的 `id`。 |
| `public String getId()` / `public void setId(String id)` | 继承自基础文档模型；setter 不会重新执行“空白 id 自动生成”的构造逻辑。 |
| `public String getText()` / `public void setText(String text)` | 继承自基础文档模型；setter 不会重新执行构造时的非 `null` 校验。 |
| `public Map<String, Object> getMetadata()` / `public void setMetadata(Map<String, Object> metadata)` | 继承自基础文档模型；getter/setter 不额外做防御性拷贝，setter 也不会把 `null` 自动转为空 Map。 |

## 说明

- 这是 `com.openjiuwen.core.retrieval.common` 包下的 Document，不是 `base_reranker` 包的基础类文档页。
- Java 实现保留 Python 侧 `id_` 的对外语义，同时使用 Java getter/setter 与 Jackson 注解承接序列化字段。
- `metadata` 是动态字典边界，Java 类型保留为 `Map<String, Object>`。
- 构造方法负责必填文本校验、空白 `id` 默认值和 metadata 入参复制；对象创建后的继承 setter 按基础模型行为直接赋值。
