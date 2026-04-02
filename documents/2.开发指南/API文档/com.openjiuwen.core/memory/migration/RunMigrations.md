# com.openjiuwen.core.memory.migration.RunMigrations

## class RunMigrations

```java
public final class RunMigrations
```

Entry point for running all memory migrations (SQL, Vector, KV).

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |

## Constructors

| Signature | Description |
| --- | --- |
| `private RunMigrations()` | Create a new `RunMigrations` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static boolean runSqlMigrations(SqlDbStore sqlDbStore)` | Execute `runSqlMigrations`. |
| `public static boolean runVectorMigrations(SemanticStore semanticStore)` | Execute `runVectorMigrations`. |
| `public static boolean runKvMigrations(BaseKVStore kvStore)` | Execute `runKvMigrations`. |
