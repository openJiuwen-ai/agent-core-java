# memory 模块缺漏复核清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\memory`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\memory`
- 本文只记录“Java 相对 Python 仍未完全对齐的公开 API / 可见语义差异”

## 默认不计入缺漏

- `snake_case -> camelCase`
- `async -> 同步方法`
- Python 模块函数折叠为 Java `static` 方法
- `BaseModel/dataclass/Enum` 改为 Java `POJO/builder/getter/enum helper`
- 私有辅助逻辑被 Java 内联实现

## 本轮结论

- 核心引擎 `LongTermMemory` 已基本对齐，公开主流程 API 未见系统性缺失。
- manager、搜索、更新检查、迁移、提取流程的主干能力基本具备。
- 当前剩余问题主要集中在“少量公开便捷接口缺失”、“migration / embedding / vector / KV 清理语义仍弱于 Python”，以及“部分 Python 公开类型在 Java 中被收口为包内实现或工具类”。

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `LongTermMemory._run_migration()` / `run_migrations.py` | Python 任一 entity 迁移失败都会抛异常，阻断后续初始化流程 | Java `RunMigrations.*` 返回 `boolean`，但 `LongTermMemory.runMigration(Runnable, ...)` 忽略返回值；只要没有异常就会记录迁移成功 | migration 失败可能被吞掉，store 注册继续进行，问题更难被发现 |
| `P1` | `LongTermMemory._get_scope_embedding_model()` | 若 scope 配置里存在 `embedding_cfg`，Python 通过 `APIEmbedding(config=...)` 动态创建并缓存 embedding 模型 | Java 工程虽已有 `APIEmbedding` 实现，但 `memory.LongTermMemory.getScopeEmbeddingModel()` 当前未接入该能力，直接 fallback 到 `registerStore(...)` 提供的全局 embedding | scope 级 embedding 配置无法生效，直接影响按 scope 隔离 embedding 模型的场景 |
| `P1` | `SemanticStore` / `VectorMigrator` | Python 依赖 `list_collection_names()`、`update_schema()`、`get_collection_metadata()`、`update_collection_metadata()` 做真实 collection 迁移 | Java 仅在底层 store 实现 `SchemaMutableVectorStore` 时才完整支持；否则 `listCollectionNames()` 退化为内存缓存、`updateSchema()` 返回 `false` | 向量 schema 迁移可能漏掉既有 collection，或因不支持而被整体跳过 |
| `P1` | `manage/mem_model/SqlDbStore` | `batch_get(table, conditions_list)` | 无 `batchGet(...)` | 公共 SQL 批量查询 API 缺失 |
| `P1` | `manage/mem_model/SqlDbStore` | `delete_table(table_name)` | 无 `deleteTable(...)` | 缺少公共删表能力 |
| `P1` | `manage/mem_model/SqlDbStore` | `get_table(table_name)` | 无 `getTable(...)` | 缺少公共 table/schema 反射入口 |
| `P1` | `migration/operation/operations.py` | `AddColumnOperation`、`RenameColumnOperation`、`UpdateColumnTypeOperation`、`AddScalarFieldOperation`、`RenameScalarFieldOperation`、`UpdateScalarFieldTypeOperation`、`UpdateEmbeddingDimensionOperation`、`UpdateKVOperation` 为公开类型 | Java 同名类位于 `Operations.java` 且为包内可见 | 外部代码无法像 Python 一样直接导入和实例化具体 operation 类型 |
| `P2` | `migrator/sql_migrator.py::SQLMigrator._migrate_update_column_type()` | Python 在 SQLite 上会重建表并复制数据，完成列类型迁移 | Java `SqlMigrator.executeUpdateColumnType(...)` 在 SQLite 上只打 warn 后返回 | SQLite 下列类型迁移退化为 no-op |
| `P2` | `manage/mem_model/user_mem_store.py::__delete_mem_id()` | Python 删除最后一个 mem id 后会删除 ids key | Java `UserMemStore.deleteMemId(...)` 删除最后一个 id 后会把空字符串写回 key | 留下空索引 key，KV 清理语义弱于 Python |
| `P2` | `manage/mem_model/db_model.py` | 公开 `UserMessage`、`ScopeUserMapping`、`MemoryMeta` 与 mixin 模型 | Java 仅保留 `DbModel.createTables(...)` 与表常量 | 若外部代码依赖 ORM 模型类型、元信息或表对象，Java 无直接对位 API |

## Python 基线自身未闭环的部分

| 位置 | 说明 | 结论 |
| --- | --- | --- |
| `manage/search/SearchManager.list_user_summary()` | Python 这里会调用 `SummaryManager.list_user_summary(...)`，但 Python `SummaryManager` 源码并未实现该方法 | 这不是 Java 独有缺漏，属于 Python 基线自身未闭环；本轮已从 Java 缺漏项中剔除 |

## 已确认不缺的部分

- `LongTermMemory.register_store/add_messages/search_user_mem/search_user_history_summary/get_user_mem_by_page` 已有 Java 对位。
- `common/base.py` 已由 `MemoryUtils` 覆盖。
- `common/crypto.py` 已由 `MemoryCrypto` 覆盖。
- `FragmentMemoryManager`、`VariableManager`、`WriteManager` 主公开方法已齐平。
- `SearchManager.search/list_user_mem/list_user_profile/get_user_variable/get_all_user_variable` 已有 Java 对位。
- `MessageManager`、`SemanticStore`、`UserMemStore` 主干公开 API 已基本对齐。
- `OperationRegistry`、`MigrationPlan`、`RunMigrations`、`SqlMigrator`、`VectorMigrator`、`KvMigrator` 主干能力已存在。
- `process.extract` 与 `prompt` 主干 API 已完成 Java 对位。
- Python `process/refine` 当前仅有空包，不构成 Java 缺漏。

## 建议修复顺序

1. 把已有 `APIEmbedding` 接入 `memory.LongTermMemory.getScopeEmbeddingModel()` 的 scope 配置链路，使其与 Python 语义一致。
2. 修正 `LongTermMemory.runMigration(...)`，不要吞掉 `RunMigrations.*` 的布尔失败结果，至少在返回 `false` 时抛错中止初始化。
3. 为 `SemanticStore` / `VectorMigrator` 补真实 collection 枚举、schema 更新、metadata 持久化能力，避免迁移退化为 no-op 或缓存态扫描。
4. 为 `SqlDbStore` 补 `batchGet(...)`、`deleteTable(...)`、`getTable(...)` 公共方法。
5. 为 `SqlMigrator` 补 SQLite 列类型迁移策略，至少做到与 Python 同级别的重建表迁移。
6. 将 `Operations.java` 中 8 个具体 operation 类型改为可公开引用的类型，至少保证与 Python 的外部可见性一致。
7. 调整 `UserMemStore.deleteMemId(...)`，在空集合时直接删除 ids key。
8. 评估是否需要为 `DbModel` 恢复公开模型类，或在文档中明确“Java 仅保留 DDL/表名层 API，不承诺 ORM 模型对齐”。