# com.openjiuwen.core.foundation.store.Embedding

## 接口 Embedding

```java
public interface Embedding
```

embedding 模型统一抽象，定义查询向量化、批量文档向量化、维度查询与最大批次能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `List<Float> embedQuery(String text)` | 对单条查询生成向量。 |
| `default List<Float> embedQuery(String text, Map<String, Object> options)` | 带选项的查询向量化，默认复用单参实现。 |
| `List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | 批量生成文档向量。 |
| `default List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | 带选项的批量向量化，默认复用基础实现。 |
| `int getDimension()` | 返回向量维度。 |
| `default int getMaxBatchSize()` | 返回最大批次，默认值为 `256`。 |
