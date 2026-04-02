# com.openjiuwen.core.memory.migration.operation.AddColumnOperation

## class AddColumnOperation

```java
public class AddColumnOperation extends BaseOperation
```

Add a new column to a table.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `table` | `String` | table. |
| `columnName` | `String` | column name. |
| `columnType` | `String` | column type. |
| `nullable` | `boolean` | nullable. |
| `defaultValue` | `Object` | default value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public AddColumnOperation(OperationMetadata metadata, String table, String columnName, String columnType, boolean nullable, Object defaultValue)` | Create a new `AddColumnOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getTable()` | Execute `getTable`. |
| `public String getColumnName()` | Execute `getColumnName`. |
| `public String getColumnType()` | Execute `getColumnType`. |
| `public boolean isNullable()` | Execute `isNullable`. |
| `public Object getDefaultValue()` | Execute `getDefaultValue`. |
