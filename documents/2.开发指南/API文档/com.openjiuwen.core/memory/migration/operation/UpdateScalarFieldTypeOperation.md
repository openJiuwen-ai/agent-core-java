# com.openjiuwen.core.memory.migration.operation.UpdateScalarFieldTypeOperation

## class UpdateScalarFieldTypeOperation

```java
public class UpdateScalarFieldTypeOperation extends BaseOperation
```

Update the data type of a scalar field in a vector data type.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `dataType` | `String` | data type. |
| `fieldName` | `String` | field name. |
| `newFieldType` | `String` | new field type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public UpdateScalarFieldTypeOperation(OperationMetadata metadata, String dataType, String fieldName, String newFieldType)` | Create a new `UpdateScalarFieldTypeOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDataType()` | Execute `getDataType`. |
| `public String getFieldName()` | Execute `getFieldName`. |
| `public String getNewFieldType()` | Execute `getNewFieldType`. |
