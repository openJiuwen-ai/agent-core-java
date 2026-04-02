# com.openjiuwen.core.foundation.store.vector.VectorStoreUtils

## class VectorStoreUtils

```java
public final class VectorStoreUtils
```

向量存储工具类，负责分值转换、字段类型映射、schema 迁移与文档变换。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static double convertL2Squared(double rawScore, double maxDist)` | 把平方 L2 距离转换为归一化分值。 |
| `public static double convertL2Squared(double rawScore)` | 使用默认最大距离 `4.0` 转换平方 L2 距离。 |
| `public static double convertCosineSimilarity(double rawScore)` | 转换 cosine similarity 分值。 |
| `public static double convertCosineDistance(double rawScore)` | 转换 cosine distance 分值。 |
| `public static double convertIpSimilarity(double rawScore)` | 转换内积分值。 |
| `public static double convertIpDistance(double rawScore)` | 转换内积距离分值。 |
| `public static VectorDataType mapStringToVectorDataType(String typeStr)` | 把字符串字段类型映射为 `VectorDataType`。 |
| `public static CollectionSchema computeNewSchema(CollectionSchema oldSchema, List<?> operations)` | 按迁移操作计算新的 schema。 |
| `public static Function<Map<String, Object>, Map<String, Object>> buildTransformFuncForOperations(List<?> operations)` | 生成文档迁移转换函数。 |

## 使用说明

- schema 迁移当前围绕 `add_field`、`rename_field`、`update_field_type`、`update_embedding_dimension` 等操作展开。
- 文档变换函数主要直接处理新增字段与字段改名；更复杂的数据变换仍需要外层配合。
