# com.openjiuwen.core.memory.migration.operation.RenameColumnOperation

## class RenameColumnOperation

```java
public class RenameColumnOperation extends BaseOperation
```

Rename a column in a table.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `table` | `String` | table. |
| `oldColumnName` | `String` | old column name. |
| `newColumnName` | `String` | new column name. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RenameColumnOperation(OperationMetadata metadata, String table, String oldColumnName, String newColumnName)` | Create a new `RenameColumnOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getTable()` | Execute `getTable`. |
| `public String getOldColumnName()` | Execute `getOldColumnName`. |
| `public String getNewColumnName()` | Execute `getNewColumnName`. |
