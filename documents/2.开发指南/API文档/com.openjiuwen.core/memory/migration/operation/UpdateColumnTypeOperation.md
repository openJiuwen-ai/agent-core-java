# com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation

## 类 UpdateColumnTypeOperation

```java
public class UpdateColumnTypeOperation
```

`UpdateColumnTypeOperation` 表示更新 SQL 列类型的迁移操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `table` | `String` | 目标表名。 |
| `columnName` | `String` | 目标列名。 |
| `newColumnType` | `String` | 迁移后的列类型。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public UpdateColumnTypeOperation(OperationMetadata metadata, String table, String columnName, String newColumnType)` | 创建一条列类型更新操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getTable()` | 返回目标表名。 |
| `public String getColumnName()` | 返回目标列名。 |
| `public String getNewColumnType()` | 返回新的列类型。 |
