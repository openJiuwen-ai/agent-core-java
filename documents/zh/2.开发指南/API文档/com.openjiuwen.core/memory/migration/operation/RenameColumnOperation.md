# com.openjiuwen.core.memory.migration.operation.RenameColumnOperation

## 类 RenameColumnOperation

```java
public class RenameColumnOperation
```

`RenameColumnOperation` 表示一次 SQL 列重命名操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `table` | `String` | 目标表名。 |
| `oldColumnName` | `String` | 原列名。 |
| `newColumnName` | `String` | 新列名。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RenameColumnOperation(OperationMetadata metadata, String table, String oldColumnName, String newColumnName)` | 创建一条列重命名操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getTable()` | 返回目标表名。 |
| `public String getOldColumnName()` | 返回旧列名。 |
| `public String getNewColumnName()` | 返回新列名。 |
