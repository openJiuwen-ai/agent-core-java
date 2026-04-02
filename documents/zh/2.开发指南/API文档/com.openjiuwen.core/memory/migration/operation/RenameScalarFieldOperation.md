# com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation

## 类 RenameScalarFieldOperation

```java
public class RenameScalarFieldOperation
```

`RenameScalarFieldOperation` 表示向量数据类型中标量字段的重命名操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dataType` | `String` | 目标向量数据类型。 |
| `oldFieldName` | `String` | 原字段名。 |
| `newFieldName` | `String` | 新字段名。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RenameScalarFieldOperation(OperationMetadata metadata, String dataType, String oldFieldName, String newFieldName)` | 创建一条标量字段重命名操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDataType()` | 返回目标数据类型。 |
| `public String getOldFieldName()` | 返回旧字段名。 |
| `public String getNewFieldName()` | 返回新字段名。 |
