# com.openjiuwen.core.memory.migration.migrator.VectorMigrator

## class VectorMigrator

```java
public class VectorMigrator
```

Vector store migrator.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `semanticStore` | `SemanticStore` | semantic store. |

## Constructors

| Signature | Description |
| --- | --- |
| `public VectorMigrator(SemanticStore semanticStore)` | Create a new `VectorMigrator` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | Execute `tryMigrate`. |
