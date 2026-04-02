# com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation

## class AddScalarFieldOperation

```java
public class AddScalarFieldOperation extends BaseOperation
```

Add a scalar field to a vector data type.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `dataType` | `String` | data type. |
| `fieldName` | `String` | field name. |
| `fieldType` | `String` | field type. |
| `defaultValue` | `Object` | default value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public AddScalarFieldOperation(OperationMetadata metadata, String dataType, String fieldName, String fieldType, Object defaultValue)` | Create a new `AddScalarFieldOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDataType()` | Execute `getDataType`. |
| `public String getFieldName()` | Execute `getFieldName`. |
| `public String getFieldType()` | Execute `getFieldType`. |
| `public Object getDefaultValue()` | Execute `getDefaultValue`. |
