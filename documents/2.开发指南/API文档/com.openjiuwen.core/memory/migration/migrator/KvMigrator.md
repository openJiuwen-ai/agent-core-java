# com.openjiuwen.core.memory.migration.migrator.KvMigrator

## class KvMigrator

```java
public class KvMigrator
```

KV data migrator with backup and rollback support.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `MAPPER` | `ObjectMapper` | mapper. |
| `KV_SCHEMA_VERSION` | `String` | kv schema version. |
| `KV_ENTITY_KEY` | `String` | kv entity key. |
| `kvStore` | `BaseKVStore` | kv store. |

## Constructors

| Signature | Description |
| --- | --- |
| `public KvMigrator(BaseKVStore kvStore)` | Create a new `KvMigrator` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | Execute `tryMigrate`. |
