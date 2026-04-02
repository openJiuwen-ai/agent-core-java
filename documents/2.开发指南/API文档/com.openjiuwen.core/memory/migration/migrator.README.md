# migrator

`com.openjiuwen.core.memory.migration.migrator` 为不同记忆后端提供实际迁移执行器，并负责记录或恢复迁移版本状态。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`KvMigrator`](./migrator/KvMigrator.md) | 执行 KV 迁移，支持自动备份与失败回滚。 |
| [`MemoryMetaManager`](./migrator/MemoryMetaManager.md) | 维护 SQL 侧 `memory_meta` 表中的版本记录。 |
| [`SqlMigrator`](./migrator/SqlMigrator.md) | 通过 JDBC 执行 SQL 列变更并更新 `memory_meta`。 |
| [`VectorMigrator`](./migrator/VectorMigrator.md) | 对向量集合应用 schema 操作并同步集合元数据版本。 |

## 关键行为

- `KvMigrator` 仅接受实体键 `kv_global`，并要求操作按 `schemaVersion` 严格递增。
- `SqlMigrator` 当前支持 `AddColumnOperation`、`RenameColumnOperation`、`UpdateColumnTypeOperation` 三类 SQL 迁移。
- `VectorMigrator` 通过 `SemanticStore.updateSchema(...)` 应用操作，并将最大版本号写回集合元数据中的 `schema_version`。
