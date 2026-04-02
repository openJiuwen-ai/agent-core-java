# com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation

## class UpdateColumnTypeOperation

```java
public class UpdateColumnTypeOperation extends BaseOperation
```

Update the data type of an existing column.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `table` | `String` | table. |
| `columnName` | `String` | column name. |
| `newColumnType` | `String` | new column type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public UpdateColumnTypeOperation(OperationMetadata metadata, String table, String columnName, String newColumnType)` | Create a new `UpdateColumnTypeOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getTable()` | Execute `getTable`. |
| `public String getColumnName()` | Execute `getColumnName`. |
| `public String getNewColumnType()` | Execute `getNewColumnType`. |
