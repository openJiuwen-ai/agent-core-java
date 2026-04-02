# com.openjiuwen.core.foundation.store.base_embedding.Embedding

## abstract class Embedding

```java
public abstract class Embedding
```

嵌入模型抽象基类，定义查询文本与文档文本的向量编码能力。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public abstract List<Float> embedQuery(String text)` | 对单条查询文本做向量编码。 |
| `public abstract List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | 批量编码文档文本，可显式指定批大小。 |
| `public List<List<Float>> embedDocuments(List<String> texts)` | 便捷重载，内部以 `batchSize = null` 调用批量编码。 |
| `public abstract int getDimension()` | 返回嵌入维度。 |

## 说明

- 该抽象类不定义具体模型实现。
- `embedDocuments(List<String>)` 不固定默认批大小，具体行为由子类在接收到 `null` 时决定。
