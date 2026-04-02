# com.openjiuwen.core.memory.migration.operation.UpdateEmbeddingDimensionOperation

## class UpdateEmbeddingDimensionOperation

```java
public class UpdateEmbeddingDimensionOperation extends BaseOperation
```

Update the embedding dimension of a vector data type.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `dataType` | `String` | data type. |
| `fieldName` | `String` | field name. |
| `newDimension` | `int` | new dimension. |
| `batchSize` | `int` | batch size. |

## Constructors

| Signature | Description |
| --- | --- |
| `public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType, String fieldName, int newDimension, int batchSize)` | Create a new `UpdateEmbeddingDimensionOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDataType()` | Execute `getDataType`. |
| `public String getFieldName()` | Execute `getFieldName`. |
| `public int getNewDimension()` | Execute `getNewDimension`. |
| `public int getBatchSize()` | Execute `getBatchSize`. |
