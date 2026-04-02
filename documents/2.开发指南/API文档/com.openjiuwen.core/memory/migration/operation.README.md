# operation

`com.openjiuwen.core.memory.migration.operation` defines migration operation metadata, registries, and concrete schema-change operations.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`AddColumnOperation`](./operation/AddColumnOperation.md) | class | Add a new column to a table. |
| [`AddScalarFieldOperation`](./operation/AddScalarFieldOperation.md) | class | Add a scalar field to a vector data type. |
| [`BaseOperation`](./operation/BaseOperation.md) | class | Base class for all migration operations. |
| [`OperationMetadata`](./operation/OperationMetadata.md) | class | Simple operation metadata. |
| [`OperationRegistry`](./operation/OperationRegistry.md) | class | Registry that manages chained upgrade operations by entity_key. |
| [`RenameColumnOperation`](./operation/RenameColumnOperation.md) | class | Rename a column in a table. |
| [`RenameScalarFieldOperation`](./operation/RenameScalarFieldOperation.md) | class | Rename a scalar field in a vector data type. |
| [`UpdateColumnTypeOperation`](./operation/UpdateColumnTypeOperation.md) | class | Update the data type of an existing column. |
| [`UpdateEmbeddingDimensionOperation`](./operation/UpdateEmbeddingDimensionOperation.md) | class | Update the embedding dimension of a vector data type. |
| [`UpdateKVOperation`](./operation/UpdateKVOperation.md) | class | Update a key-value pair via a provided callable. |
| [`UpdateScalarFieldTypeOperation`](./operation/UpdateScalarFieldTypeOperation.md) | class | Update the data type of a scalar field in a vector data type. |

## Notes

- The current page also links the 11 direct public type page(s) defined in this package.
