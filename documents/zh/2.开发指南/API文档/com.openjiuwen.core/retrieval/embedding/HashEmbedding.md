# com.openjiuwen.core.retrieval.embedding.HashEmbedding

## 类 HashEmbedding

```java
public class HashEmbedding implements Embedding
```

基于 SHA-256 的本地确定性 embedding 实现，适合测试或无远程模型时使用。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public HashEmbedding()` | 创建默认维度为 `32`、默认最大批次为 `256` 的实例。 |
| `public HashEmbedding(int dimension, int maxBatchSize)` | 指定维度与最大批次；两者最小值都为 `1`。 |

## 说明

- 相同文本会得到稳定一致的向量结果。
- `null` 文本会按空字符串处理。
- 测试确认所有向量值都位于 `[-1, 1]`。
