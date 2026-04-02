# com.openjiuwen.core.memory.migration.operation.UpdateKVOperation

## class UpdateKVOperation

```java
public class UpdateKVOperation extends BaseOperation
```

Update a key-value pair via a provided callable.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `updateFunc` | `Consumer<BaseKVStore>` | update func. |

## Constructors

| Signature | Description |
| --- | --- |
| `public UpdateKVOperation(OperationMetadata metadata, Consumer<BaseKVStore> updateFunc)` | Create a new `UpdateKVOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Consumer<BaseKVStore> getUpdateFunc()` | Execute `getUpdateFunc`. |
