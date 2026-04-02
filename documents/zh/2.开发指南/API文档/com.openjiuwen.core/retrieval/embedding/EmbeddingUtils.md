# com.openjiuwen.core.retrieval.embedding.EmbeddingUtils

## 类 EmbeddingUtils

```java
public final class EmbeddingUtils
```

embedding 辅助工具，当前主要负责把 base64 little-endian float32 向量解析为 `List<Float>`。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static List<Float> parseBase64Embedding(String base64Embedding)` | 解析 base64 float32 向量。 |

## 说明

- 输入为空白时会抛出检索异常。
- 若字节长度不是 `Float.BYTES` 的整数倍，也会报错。
