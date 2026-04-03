# com.openjiuwen.core.memory.migration.operation.AddColumnOperation

## 类 AddColumnOperation

```java
public class AddColumnOperation
```

`AddColumnOperation` 表示一次 SQL 新增列操作。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `table` | `String` | 目标表名。 |
| `columnName` | `String` | 新增列名。 |
| `columnType` | `String` | 新增列的 SQL 类型。 |
| `nullable` | `boolean` | 新增列是否允许 `NULL`。 |
| `defaultValue` | `Object` | 新增列的默认值；可为 `null`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AddColumnOperation(OperationMetadata metadata, String table, String columnName, String columnType, boolean nullable, Object defaultValue)` | 创建一条新增列操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getTable()` | 返回目标表名。 |
| `public String getColumnName()` | 返回列名。 |
| `public String getColumnType()` | 返回列类型。 |
| `public boolean isNullable()` | 返回该列是否允许为空。 |
| `public Object getDefaultValue()` | 返回默认值对象。 |
