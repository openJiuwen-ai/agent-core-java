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
- 前一轮确认的主干缺漏里，migration 失败传播、scope 级 embedding 动态实例化、`SqlDbStore` 便捷接口、SQLite 列类型迁移、`UserMemStore` 空 key 清理、migration operation 公开可见性、`DbModel` 公开模型载体均已补齐。
- 当前剩余问题主要集中在 vector migration 对底层 `SchemaMutableVectorStore` 能力的依赖，以及 `DbModel` 仍未提供 Python ORM `Table/mixin` 级别元编程接口。

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `SemanticStore` / `VectorMigrator` | Python 依赖 `list_collection_names()`、`update_schema()`、`get_collection_metadata()`、`update_collection_metadata()` 做真实 collection 迁移 | Java 仅在底层 store 实现 `SchemaMutableVectorStore` 时才完整支持；否则 `listCollectionNames()` 退化为内存缓存、`updateSchema()` 返回 `false` | 向量 schema 迁移可能漏掉既有 collection，或因不支持而被整体跳过 |
| `P2` | `manage/mem_model/db_model.py` | Python 公开 `UserMessage`、`ScopeUserMapping`、`MemoryMeta`、mixin 与 `Table` 对象 | Java 已补公开 record 模型与 DDL 工具类，但仍不提供 SQLAlchemy `Table` / mixin 层元数据接口 | 若外部代码依赖 Python ORM 元编程能力，Java 仍需额外桥接 |

## Python 基线自身未闭环的部分

| 位置 | 说明 | 结论 |
| --- | --- | --- |
| `manage/search/SearchManager.list_user_summary()` | Python 这里会调用 `SummaryManager.list_user_summary(...)`，但 Python `SummaryManager` 源码并未实现该方法 | 这不是 Java 独有缺漏，属于 Python 基线自身未闭环；本轮已从 Java 缺漏项中剔除 |

## 已确认不缺的部分

- `LongTermMemory.register_store/add_messages/search_user_mem/search_user_history_summary/get_user_mem_by_page` 已有 Java 对位。
- `LongTermMemory._run_migration()` 的失败传播与上抛语义已补齐。
- `LongTermMemory._get_scope_embedding_model()` 已接入 `APIEmbedding` 动态实例化与缓存。
- `common/base.py` 已由 `MemoryUtils` 覆盖。
- `common/crypto.py` 已由 `MemoryCrypto` 覆盖。
- `FragmentMemoryManager`、`VariableManager`、`WriteManager` 主公开方法已齐平。
- `SearchManager.search/list_user_mem/list_user_profile/get_user_variable/get_all_user_variable` 已有 Java 对位。
- `MessageManager`、`SemanticStore`、`UserMemStore` 主干公开 API 已基本对齐。
- `SqlDbStore.batchGet/deleteTable/getTable` 已补齐。
- `SqlMigrator` 已补 SQLite 列类型迁移路径。
- `UserMemStore.deleteMemId(...)` 已改为在空集合时直接删除 ids key。
- migration operation 8 个具体类型已改为公开独立类。
- Java 已补 `UserMessage`、`ScopeUserMapping`、`MemoryMeta` 公开模型载体。
- `OperationRegistry`、`MigrationPlan`、`RunMigrations`、`SqlMigrator`、`VectorMigrator`、`KvMigrator` 主干能力已存在。
- `process.extract` 与 `prompt` 主干 API 已完成 Java 对位。
- Python `process/refine` 当前仅有空包，不构成 Java 缺漏。

## 建议修复顺序

1. 为 `SemanticStore` / `VectorMigrator` 补真实 collection 枚举、schema 更新、metadata 持久化能力，尤其是 Milvus 等非 `SchemaMutableVectorStore` 实现。
2. 评估是否需要为 `DbModel` 继续暴露 Python 风格 `Table/mixin` 层 API；如果不打算提供，应在文档中明确 Java 仅承诺公开记录模型与 DDL 层能力。