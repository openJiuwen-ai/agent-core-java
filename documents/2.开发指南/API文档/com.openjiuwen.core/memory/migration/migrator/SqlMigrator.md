# com.openjiuwen.core.memory.migration.migrator.SqlMigrator

## class SqlMigrator

```java
public class SqlMigrator
```

SQL schema migrator using JDBC.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `sqlDb` | `SqlDbStore` | sql db. |
| `memoryMetaManager` | `MemoryMetaManager` | memory meta manager. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SqlMigrator(SqlDbStore sqlDb)` | Create a new `SqlMigrator` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | Execute `tryMigrate`. |
