# com.openjiuwen.core.memory.migration.migrator.MemoryMetaManager

## class MemoryMetaManager

```java
public class MemoryMetaManager
```

Manages memory_meta table for tracking migration schema versions.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `sqlDb` | `SqlDbStore` | sql db. |
| `META_TABLE` | `String` | meta table. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MemoryMetaManager(SqlDbStore sqlDb)` | Create a new `MemoryMetaManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void add(String tableName, String schemaVersion)` | Execute `add`. |
| `public boolean deleteByTableName(String tableName)` | Execute `deleteByTableName`. |
| `public List<Map<String, Object>> getByTableName(String tableName)` | Execute `getByTableName`. |
