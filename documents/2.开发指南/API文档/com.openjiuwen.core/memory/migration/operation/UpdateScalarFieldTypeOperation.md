# com.openjiuwen.core.memory.migration.operation.UpdateScalarFieldTypeOperation

## 类 UpdateScalarFieldTypeOperation

```java
public class UpdateScalarFieldTypeOperation
```

`UpdateScalarFieldTypeOperation` 表示更新向量数据类型中标量字段类型的迁移操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dataType` | `String` | 目标向量数据类型。 |
| `fieldName` | `String` | 目标字段名。 |
| `newFieldType` | `String` | 更新后的字段类型。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public UpdateScalarFieldTypeOperation(OperationMetadata metadata, String dataType, String fieldName, String newFieldType)` | 创建一条字段类型更新操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDataType()` | 返回目标数据类型。 |
| `public String getFieldName()` | 返回目标字段名。 |
| `public String getNewFieldType()` | 返回新的字段类型。 |
