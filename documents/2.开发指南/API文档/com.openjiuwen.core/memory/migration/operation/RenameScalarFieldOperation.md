# com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation

## class RenameScalarFieldOperation

```java
public class RenameScalarFieldOperation extends BaseOperation
```

Rename a scalar field in a vector data type.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `dataType` | `String` | data type. |
| `oldFieldName` | `String` | old field name. |
| `newFieldName` | `String` | new field name. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RenameScalarFieldOperation(OperationMetadata metadata, String dataType, String oldFieldName, String newFieldName)` | Create a new `RenameScalarFieldOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDataType()` | Execute `getDataType`. |
| `public String getOldFieldName()` | Execute `getOldFieldName`. |
| `public String getNewFieldName()` | Execute `getNewFieldName`. |
