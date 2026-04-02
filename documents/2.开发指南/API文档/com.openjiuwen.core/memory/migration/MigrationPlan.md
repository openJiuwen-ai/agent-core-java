# com.openjiuwen.core.memory.migration.MigrationPlan

## class MigrationPlan

```java
public final class MigrationPlan
```

Global migration registries for SQL, vector, and KV operations.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `SQL_REGISTRY` | `OperationRegistry` | sql registry. |
| `VECTOR_REGISTRY` | `OperationRegistry` | vector registry. |
| `KV_REGISTRY` | `OperationRegistry` | kv registry. |

## Constructors

| Signature | Description |
| --- | --- |
| `private MigrationPlan() {}` | Create a new `MigrationPlan` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static OperationRegistry getSqlRegistry()` | Execute `getSqlRegistry`. |
| `public static OperationRegistry getVectorRegistry()` | Execute `getVectorRegistry`. |
| `public static OperationRegistry getKvRegistry()` | Execute `getKvRegistry`. |

## Notes

- Related tests: `MigrationPlanTest.java`
