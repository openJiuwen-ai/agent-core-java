# com.openjiuwen.core.memory.migration.operation.UpdateEmbeddingDimensionOperation

## 类 UpdateEmbeddingDimensionOperation

```java
public class UpdateEmbeddingDimensionOperation
```

`UpdateEmbeddingDimensionOperation` 表示更新向量字段 embedding 维度的迁移操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dataType` | `String` | 目标向量数据类型。 |
| `fieldName` | `String` | 目标向量字段名。 |
| `newDimension` | `int` | 更新后的 embedding 维度。 |
| `batchSize` | `int` | 批量迁移时使用的批大小。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType, String fieldName, int newDimension, int batchSize)` | 创建一条 embedding 维度更新操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDataType()` | 返回目标数据类型。 |
| `public String getFieldName()` | 返回目标字段名。 |
| `public int getNewDimension()` | 返回新的 embedding 维度。 |
| `public int getBatchSize()` | 返回批处理大小。 |
