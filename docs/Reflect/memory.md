# memory 模块 Python↔Java API 映射复核

## 1. 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\memory`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\memory`
- 输出目标: 梳理文件/类/方法 API 映射关系，并识别 Java 相对 Python 的真实缺漏

## 2. 复核口径

以下差异默认不计入“缺漏”:

- `snake_case -> camelCase`
- `async def -> 同步方法`
- Python 模块函数收口为 Java `static` helper
- `dataclass / BaseModel / Enum` 在 Java 中改为 `POJO / builder / getter / enum helper`
- 私有辅助函数被 Java 内联实现，但对外语义不变

## 3. 总体结论

- memory 模块整体上已经完成主干转译，顶层引擎、各类 manager、搜索、更新检查、迁移、提取、提示词加载均可一一对位。
- 核心入口 `LongTermMemory` 的公开能力基本齐全，Python 中常用的 `register_store`、`add_messages`、`search_user_mem`、`search_user_history_summary`、`get_user_mem_by_page` 等，在 Java 侧均已有对应公开方法。
- 当前需要重点关注的不是整体缺包，而是少数公开 API 与功能语义未完全保留，主要集中在:
  - migration 结果传递与失败上抛语义
  - `SqlDbStore` 的部分公开便捷方法
  - scope 级 embedding 动态实例化能力
  - vector migration 对底层 store 能力的依赖
  - SQLite 场景下的 SQL migration 降级处理
  - `UserMemStore` 删除索引后的空 key 清理语义
  - migration operation 具体操作类的公开可见性
  - `DbModel` 的 ORM 模型类被收口为 DDL 工具类

## 4. 文件、类、方法映射

### 4.1 顶层入口

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `long_term_memory.py::LongTermMemory` | `LongTermMemory` | `register_store -> registerStore`；`set_config -> setConfig`；`set_scope_config -> setScopeConfig`；`get_scope_config -> getScopeConfig`；`delete_scope_config -> deleteScopeConfig`；`delete_mem_by_scope -> deleteMemByScope`；`add_messages -> addMessages`；`get_recent_messages -> getRecentMessages`；`get_message_by_id -> getMessageById`；`delete_messages_by_user_and_scope -> deleteMessagesByUserAndScope`；`delete_mem_by_id -> deleteMemById`；`delete_mem_by_user_id -> deleteMemByUserId`；`update_mem_by_id -> updateMemById`；`get_variables -> getVariables`；`update_variables -> updateVariables`；`delete_variables -> deleteVariables`；`search_user_mem -> searchUserMem`；`search_user_history_summary -> searchUserHistorySummary`；`user_mem_total_num -> userMemTotalNum`；`get_user_mem_by_page -> getUserMemByPage` | 主入口 API 基本对齐 |
| `long_term_memory.py::MemInfo` | `MemInfo` | Python 数据模型 -> Java POJO/Builder | 对齐 |
| `long_term_memory.py::MemResult` | `MemResult` | Python 数据模型 -> Java POJO/Builder | 对齐 |

补充说明:

- Python 私有辅助方法 `_get_scope_llm`、`_get_scope_embedding_model`、`_get_history_messages`、`_run_migration` 在 Java 侧分别对应 `getScopeLlm`、`getScopeEmbeddingModel`、`getHistoryMessages`、`runMigration`。
- Java 额外提供 `getInstance()` / `resetInstance()`，用于单例获取与测试重置；Python 使用 `metaclass=Singleton`。

### 4.2 `common`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `common/base.py` | `common/MemoryUtils.java` | `generate_idx_name -> generateIdxName`；`parse_memtype_from_idx_name -> parseMemTypeFromIdxName`；`parse_memory_hit_infos -> parseMemoryHitInfos` | 对齐，Python 模块函数被收口为 Java 静态工具类 |
| `common/crypto.py` | `common/MemoryCrypto.java` | `encrypt -> encrypt`；`decrypt -> decrypt` | 对齐 |
| `common/distributed_lock.py::DistributedLock` | `common/DistributedLock` | `acquire -> acquire`；`release -> release`；Python `__aenter__/__aexit__` -> Java `AutoCloseable.close()` | 语义对齐，承载方式不同 |
| `common/kv_prefix_registry.py::KvPrefixRegistry` | `common/KvPrefixRegistry` | `register_current -> registerCurrent`；`register_legacy -> registerLegacy`；`get_all_prefixes -> getAllPrefixes`；`unregister -> unregister` | 对齐 |

Java 额外补充:

- `MemoryUtils.HitParseResult` 用于承载 Python `tuple[list[str], dict[str, float]]` 的返回值。

### 4.3 `config`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `config.py::MemoryEngineConfig` | `MemoryEngineConfig` | `check_crypto_key -> validateCryptoKey` | 对齐 |
| `config.py::MemoryScopeConfig` | `MemoryScopeConfig` | 配置模型字段映射 | 对齐 |
| `config.py::AgentMemoryConfig` | `AgentMemoryConfig` | 配置模型字段映射 | 对齐 |

### 4.4 `manage.index`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `BaseMemoryManager` | `BaseMemoryManager` | `add_memories -> addMemories`；`update -> update`；`delete -> delete`；`delete_by_user_id -> deleteByUserId`；`get -> get`；`search -> search`；`encrypt_memory_if_needed -> encryptMemoryIfNeeded`；`decrypt_memory_if_needed -> decryptMemoryIfNeeded` | 抽象基类语义对齐 |
| `FragmentMemoryManager` | `FragmentMemoryManager` | `add_memories -> addMemories`；`update -> update`；`search -> search`；`get -> get`；`delete -> delete`；`delete_by_user_id -> deleteByUserId`；`list_fragment_memories -> listFragmentMemories` | 主公开 API 对齐 |
| `SummaryManager` | `SummaryManager` | `add_memories -> addMemories`；`update -> update`；`delete -> delete`；`delete_by_user_id -> deleteByUserId`；`get -> get`；`search -> search` | 核心 CRUD/检索对齐 |
| `VariableManager` | `VariableManager` | `add_memories -> addMemories`；`update -> update`；`update_user_variable -> updateUserVariable`；`delete -> delete`；`delete_by_user_id -> deleteByUserId`；`delete_user_variable -> deleteUserVariable`；`get -> get`；`search -> search`；`query_variable -> queryVariable` | 对齐 |
| `WriteManager` | `WriteManager` | `add_memories -> addMemories`；`update_mem_by_id -> updateMemById`；`delete_mem_by_id -> deleteMemById`；`delete_mem_by_user_id -> deleteMemByUserId` | 对齐 |

说明:

- Python `RecallParams`、`AddVectorParams`、`FragmentMemoryStoreParams` 是 `FragmentMemoryManager` 内部辅助 DTO；Java 通过参数展开和局部变量完成同一逻辑，没有单独建模。
- Python `SummaryManager` 的 `_delete_vector_summary_memory`、`_recall_by_vector`、`_delete_vector_store_table` 在 Java 中被内联进公开方法，不影响主流程，但会影响“按类/按方法一一映射”的表面一致性。

### 4.5 `manage.search`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `search_manager.py::SearchParams` | `SearchParams` | 字段模型映射 | 对齐 |
| `search_manager.py::SearchManager` | `SearchManager` | `search -> search`；`list_user_mem -> listUserMem`；`list_user_profile -> listUserProfile`；`get_user_variable -> getUserVariable`；`get_all_user_variable -> getAllUserVariable` | 主可用公开 API 对齐 |

补充说明:

- Python `SearchManager.list_user_summary()` 在源码中调用 `SummaryManager.list_user_summary(...)`，但 Python `SummaryManager` 本身并没有实现该方法。这一项属于 Python 基线自身未闭环，不能直接判定为 Java 独有缺漏。

### 4.6 `manage.update`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `mem_update_checker.py::CheckResult` | `CheckResult` | 枚举值对齐；Java 额外 `fromValue/getValue` | 对齐 |
| `mem_update_checker.py::MemoryStatus` | `MemoryStatus` | 枚举值对齐；Java 额外 `fromValue/getValue` | 对齐 |
| `mem_update_checker.py::MemoryActionItem` | `MemoryActionItem` | 数据模型映射 | 对齐 |
| `mem_update_checker.py::MemCheckItem` | `MemCheckItem` | 数据模型映射 | 对齐 |
| `mem_update_checker.py::MemUpdateChecker` | `MemUpdateChecker` | `check -> check`；Java 额外拆出 `formatMemories`、`parseCheckItem` 私有实现 | 对齐 |

### 4.7 `manage.mem_model`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `memory_unit.py::MemoryType` | `MemoryType` | 枚举值对齐；Java 额外 `fromValue/getValue` | 对齐 |
| `memory_unit.py::SupportMemoryType` | `SupportMemoryType` | 枚举值对齐；Java 额外 `getValue` | 对齐 |
| `memory_unit.py::BaseMemoryUnit` | `BaseMemoryUnit` | 基类映射 | 对齐 |
| `memory_unit.py::FragmentMemoryUnit` | `FragmentMemoryUnit` | 数据模型映射；Java 额外 `getMemType` | 对齐 |
| `memory_unit.py::VariableUnit` | `VariableUnit` | 数据模型映射；Java 额外 `getMemType/getMemId` | 对齐 |
| `memory_unit.py::SummaryUnit` | `SummaryUnit` | 数据模型映射；Java 额外 `getMemType` | 对齐 |
| `data_id_manager.py::DataIdManager` | `DataIdManager` | `generate_next_id -> generateNextId` | 对齐 |
| `message_manager.py::MessageAddRequest` | `MessageAddRequest` | 数据模型映射 | 对齐 |
| `message_manager.py::MessageManager` | `MessageManager` | `add -> add`；`get -> get`；`get_by_id -> getById`；`delete_by_user_and_scope -> deleteByUserAndScope` | 对齐，返回载体由 tuple/list 改为 `MessageRecord` |
| `scope_user_mapping_manager.py::ScopeUserMappingManager` | `ScopeUserMappingManager` | `add -> add`；`delete_by_scope_id -> deleteByScopeId`；`get_by_scope_id -> getByScopeId` | 对齐 |
| `semantic_store.py::SemanticStore` | `SemanticStore` | `initialize_embedding_model -> initializeEmbeddingModel`；`add_docs -> addDocs`；`delete_docs -> deleteDocs`；`delete_table -> deleteTable`；`search -> search` | 核心 API 对齐；Java 还补了 collection/schema 维护方法，但部分能力依赖底层 `SchemaMutableVectorStore` |
| `sql_db_store.py::SqlDbStore` | `SqlDbStore` | `write -> write`；`get -> get`；`get_with_sort -> getWithSort`；`exist -> exist`；`condition_get -> conditionGet`；`update -> update`；`delete -> delete` | 基本对齐，但 `batch_get` / `delete_table` / `get_table` 缺失 |
| `user_mem_store.py::UserMemStore` | `UserMemStore` | `write -> write`；`update -> update`；`delete -> delete`；`batch_delete -> batchDelete`；`get -> get`；`batch_get -> batchGet`；`get_all -> getAll`；`get_by_topic -> getByTopic`；`get_in_range -> getInRange` | 对齐 |
| `db_model.py` | `DbModel` | Python `create_tables -> createTables`；Python ORM mixin/model 类被 Java 收口到 DDL 工具类与常量 | 功能主线存在，但公开建模 API 不完全一致 |

### 4.8 `migration` / `operation`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `operation/base_operation.py::OperationMetadata` | `OperationMetadata` | 元数据模型映射 | 对齐 |
| `operation/base_operation.py::BaseOperation` | `BaseOperation` | `schema_version -> getSchemaVersion`；`description -> getDescription` | 对齐 |
| `operation/operations.py::{AddColumnOperation, RenameColumnOperation, UpdateColumnTypeOperation, AddScalarFieldOperation, RenameScalarFieldOperation, UpdateScalarFieldTypeOperation, UpdateEmbeddingDimensionOperation, UpdateKVOperation}` | `Operations.java` 内同名类 | 字段语义对齐 | Java 仅保留包内可见类，公开可见性弱于 Python |
| `operation/operation_registry.py::OperationRegistry` | `OperationRegistry` | `register -> register`；`get_operations -> getOperations`；`get_current_version -> getCurrentVersion`；`get_all_entities -> getAllEntities`；`get_all_operations -> getAllOperations`；`clear -> clear`；`set_operations -> setOperations` | 对齐 |
| `migration_plan.py` | `MigrationPlan` | `get_sql_registry -> getSqlRegistry`；`get_vector_registry -> getVectorRegistry`；`get_kv_registry -> getKvRegistry` | 对齐 |
| `migrator/memory_meta_manager.py::MemoryMetaManager` | `MemoryMetaManager` | `add -> add`；`delete_by_table_name -> deleteByTableName`；`get_by_table_name -> getByTableName` | 对齐 |
| `migrator/sql_migrator.py::SQLMigrator` | `SqlMigrator` | `try_migrate -> tryMigrate` | 对齐 |
| `migrator/vector_migrator.py::VectorMigrator` | `VectorMigrator` | `try_migrate -> tryMigrate`；`_find_collections -> findCollections` | 对齐 |
| `migrator/kv_migrator.py::KVMigrator` | `KvMigrator` | `try_migrate -> tryMigrate`；`_validate_operations_order -> validateOperationsOrder` | 对齐 |
| `run_migrations.py` | `RunMigrations` | `run_sql_migrations -> runSqlMigrations`；`run_vector_migrations -> runVectorMigrations`；`run_kv_migrations -> runKvMigrations` | 入口存在，但失败传播语义弱于 Python |

### 4.9 `process.extract` / `prompt`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `extract/common.py::ExtractMemoryParams` | `ExtractMemoryParams` | 参数模型映射 | 对齐 |
| `extract/memory_analyzer.py::{VariableResult, MemoryAnalyzerResult}` | `VariableResult` / `MemoryAnalyzerResult` | 数据模型映射 | 对齐 |
| `extract/memory_analyzer.py::MemoryAnalyzer` | `MemoryAnalyzer` | `analyze -> analyze` | 对齐；Java 改为静态工具类 |
| `extract/long_term_memory_extractor.py::LongTermMemoryExtractor` | `LongTermMemoryExtractor` | `extract_long_term_memory -> extractLongTermMemory` | 对齐；Java 改为静态工具类 |
| `extract/generation.py::Generator` | `Generator` | `gen_all_memory -> genAllMemory`；`_process_extracted_data -> processExtractedData` | 对齐 |
| `prompt/prompt_applier.py::PromptApplier` | `PromptApplier` | `apply -> apply`；`clear_cache -> clearCache`；`get_template -> getTemplate` | 对齐 |

补充说明:

- Python `process/refine` 当前仅有空 `__init__.py`，未承载实际 API；Java 未建立对应包不构成缺漏。

## 5. Java 侧额外补充能力

以下为 Java 侧相对 Python 的显式补充，不属于缺漏:

- `LongTermMemory.resetInstance()`
- `PromptApplier.getInstance()`
- `MemoryUtils.HitParseResult`
- 各枚举的 `fromValue()` / `getValue()`
- `SemanticStore` 的 `collectionExist`、`createCollection`、`listCollectionNames`、`getCollectionMetadata`、`updateCollectionMetadata`、`updateSchema`

## 6. 本轮确认的真实缺漏

### 6.1 高优先级

| 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- |
| `LongTermMemory._run_migration()` / `run_migrations.py` | Python 在任一 entity 迁移失败时抛异常，`LongTermMemory._run_migration()` 统一捕获后再包装上抛 | Java `RunMigrations.*` 仅返回 `boolean`；`LongTermMemory.runMigration(Runnable, ...)` 用 `Runnable` 调用时忽略返回值，只要没有抛异常就记录“migration completed successfully” | 某些 migration 失败会被吞成日志，store 注册流程可能继续向下执行，失败可观测性与阻断语义弱于 Python |
| `LongTermMemory._get_scope_embedding_model()` | 若 scope 配置含 `embedding_cfg`，通过 `APIEmbedding(config=...)` 动态创建并缓存 embedding 模型 | Java 工程虽然已有 `com.openjiuwen.core.retrieval.embedding.APIEmbedding`，但 `getScopeEmbeddingModel()` 当前未接入这条链路，直接返回 `null` | scope 级 embedding 配置在 Java memory 模块中不生效，语义弱于 Python |
| `SemanticStore` / `VectorMigrator` | Python 可依赖 `BaseVectorStore.list_collection_names()`、`update_schema()`、`get_collection_metadata()`、`update_collection_metadata()` 做真实 collection 发现与 schema 迁移 | Java 仅在底层实现 `SchemaMutableVectorStore` 时才具备完整能力；否则 `listCollectionNames()` 退化为内存缓存，`updateSchema()` 直接告警返回 `false` | 既有 collection 的向量迁移与 schema 维护能力不稳定，可能漏迁或直接跳过 |
| `manage/mem_model/SqlDbStore` | `batch_get(table, conditions_list)` | 无 `batchGet(...)` | SQL store 公共 API 未齐平 |
| `manage/mem_model/SqlDbStore` | `delete_table(table_name)` | 无 `deleteTable(...)` | 缺少公开删表能力 |
| `manage/mem_model/SqlDbStore` | `get_table(table_name)` | 无 `getTable(...)` | 缺少公开 schema/table 反射入口 |
| `migration/operation/operations.py` | 8 个 operation 类型对外可直接导入/实例化 | Java 将同名类放入 `Operations.java` 且为包内可见 | 外部调用方无法像 Python 一样直接引用具体迁移操作类 |

### 6.2 中优先级

| 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- |
| `migrator/sql_migrator.py::SQLMigrator._migrate_update_column_type()` | Python 在 SQLite 上走“重建新表并复制数据”的 workaround，继续执行列类型迁移 | Java `SqlMigrator.executeUpdateColumnType(...)` 在 SQLite 上仅 `warn` 后返回，不执行等价迁移 | SQLite 环境下列类型迁移会退化为 no-op，schema 演进能力弱于 Python |
| `manage/mem_model/user_mem_store.py::__delete_mem_id()` | Python 删除最后一个 mem id 后会删除 ids key | Java `UserMemStore.deleteMemId(...)` 在最后一个 id 删除后会把空字符串写回 key | 会遗留空索引 key，和 Python 的 KV 清理语义不一致 |
| `manage/mem_model/db_model.py` | 公开 `UserMessage`、`ScopeUserMapping`、`MemoryMeta` 及 mixin 模型 | Java 仅保留 `DbModel.createTables(...)` 和表名常量 | 若外部代码依赖 ORM 模型类型，Java 无直接对位类 |

## 7. 结论

- 从 memory 引擎主干能力看，Java 版已经覆盖 Python 版绝大多数对外 API。
- 当前真正需要在“缺漏清单”中跟进的，是少量公开便捷 API、scope embedding 动态能力、vector migration 语义，以及公开类型可见性问题，而不是整体架构缺失。
- 建议后续补齐顺序: `migration 失败结果上抛 -> scope embedding 动态实例化 -> vector migration 真实 collection/schema 能力 -> SqlDbStore 缺失便捷方法 -> SQLite 列类型迁移补齐 -> migration operation 公开化 -> UserMemStore 空 key 清理 -> DbModel 公开模型策略`。