# com.openjiuwen.core.memory.migration.operation.BaseOperation

## class BaseOperation

```java
public abstract class BaseOperation
```

Base class for all migration operations.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `metadata` | `OperationMetadata` | metadata. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected BaseOperation(OperationMetadata metadata)` | Create a new `BaseOperation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public int getSchemaVersion()` | Execute `getSchemaVersion`. |
| `public String getDescription()` | Execute `getDescription`. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
