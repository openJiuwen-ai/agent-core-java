# migrator

`com.openjiuwen.core.memory.migration.migrator` contains backend-specific migration runners for KV, SQL, vector, and metadata stores.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`KvMigrator`](./migrator/KvMigrator.md) | class | KV data migrator with backup and rollback support. |
| [`MemoryMetaManager`](./migrator/MemoryMetaManager.md) | class | Manages memory_meta table for tracking migration schema versions. |
| [`SqlMigrator`](./migrator/SqlMigrator.md) | class | SQL schema migrator using JDBC. |
| [`VectorMigrator`](./migrator/VectorMigrator.md) | class | Vector store migrator. |

## Notes

- The current page also links the 4 direct public type page(s) defined in this package.
