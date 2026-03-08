# Memory模块 Python→Java 转译报告

## 1. 概述

本报告记录了将Python `openjiuwen.core.memory` 模块转译为Java `com.openjiuwen.core.memory` 包的完整过程。转译基于设计文档（01-overview至06-coding-standards）以及Python源码（约50个文件），共生成53个Java源文件和3个资源文件。

## 2. 转译文件清单

### 2.1 主入口类（`com.openjiuwen.core.memory`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `LongTermMemory.java` | `long_term_memory.py` | 主入口单例类，全部异步方法转为同步 |
| `MemInfo.java` | `long_term_memory.py::MemInfo` | 记忆信息数据类 |
| `MemResult.java` | `long_term_memory.py::MemResult` | 搜索结果数据类（含相关度分数） |

### 2.2 通用包（`common`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `MemoryUtils.java` | `common/utils.py` | 工具方法：ID生成、索引名解析、命中信息解析 |
| `MemoryCrypto.java` | `common/crypto.py` | AES-256-GCM加解密（BouncyCastle实现） |
| `DistributedLock.java` | `common/distributed_lock.py` | 基于KV Store的分布式锁（AutoCloseable） |
| `KvPrefixRegistry.java` | `common/kv_prefix_registry.py` | KV前缀注册表（饿汉单例） |

### 2.3 配置包（`config`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `MemoryEngineConfig.java` | `config/config.py::MemoryEngineConfig` | 系统级配置 |
| `MemoryScopeConfig.java` | `config/config.py::MemoryScopeConfig` | 作用域级配置 |
| `AgentMemoryConfig.java` | `config/config.py::AgentMemoryConfig` | Agent记忆配置 |

### 2.4 数据模型包（`manage.mem_model`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `MemoryType.java` | `mem_model/memory_unit.py::MemoryType` | 记忆类型枚举 |
| `SupportMemoryType.java` | `mem_model/memory_unit.py::SupportMemoryType` | 支持的记忆类型枚举 |
| `BaseMemoryUnit.java` | `mem_model/memory_unit.py::BaseMemoryUnit` | 记忆单元基类 |
| `FragmentMemoryUnit.java` | `mem_model/memory_unit.py::FragmentMemoryUnit` | 片段记忆单元 |
| `VariableUnit.java` | `mem_model/memory_unit.py::VariableUnit` | 变量记忆单元 |
| `SummaryUnit.java` | `mem_model/memory_unit.py::SummaryUnit` | 摘要记忆单元 |
| `DataIdManager.java` | `mem_model/data_id_manager.py` | 24字符十六进制ID生成器 |
| `SqlDbStore.java` | `mem_model/sql_db_store.py` | JDBC封装（写入/查询/更新/删除） |
| `DbModel.java` | `mem_model/db_model.py` | DDL建表（user_message/scope_user_mapping/memory_meta） |
| `UserMemStore.java` | `mem_model/user_mem_store.py` | 基于KV Store的用户记忆存储 |
| `SemanticStore.java` | `mem_model/semantic_store.py` | 向量存储封装（内置Embedding调用） |
| `MessageAddRequest.java` | `mem_model/message_manager.py::MessageAddRequest` | 消息添加请求 |
| `MessageManager.java` | `mem_model/message_manager.py` | 消息管理器（含加解密） |
| `ScopeUserMappingManager.java` | `mem_model/scope_user_mapping_manager.py` | 作用域-用户映射管理 |

### 2.5 索引管理包（`manage.index`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `BaseMemoryManager.java` | `index/base_memory_manager.py` | 抽象基类（含加解密静态方法） |
| `FragmentMemoryManager.java` | `index/fragment_memory_manager.py` | 片段记忆管理器（最复杂，~260行） |
| `SummaryManager.java` | `index/summary_manager.py` | 摘要记忆管理器 |
| `VariableManager.java` | `index/variable_manager.py` | 变量记忆管理器 |
| `WriteManager.java` | `index/write_manager.py` | 写入编排器 |

### 2.6 搜索包（`manage.search`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `SearchParams.java` | `search/search_manager.py::SearchParams` | 搜索参数 |
| `SearchManager.java` | `search/search_manager.py` | 搜索编排器 |

### 2.7 更新检查包（`manage.update`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `CheckResult.java` | `update/mem_update_checker.py::CheckResult` | 检查结果枚举 |
| `MemoryStatus.java` | `update/mem_update_checker.py::MemoryStatus` | 记忆状态枚举（ADD/DELETE） |
| `MemoryActionItem.java` | `update/mem_update_checker.py::MemoryActionItem` | 记忆操作项 |
| `MemCheckItem.java` | `update/mem_update_checker.py::MemCheckItem` | 记忆检查项 |
| `MemUpdateChecker.java` | `update/mem_update_checker.py` | LLM驱动的记忆更新检查器 |

### 2.8 迁移包（`migration`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `OperationMetadata.java` | `operation/base_operation.py::OperationMetadata` | 迁移操作元数据 |
| `BaseOperation.java` | `operation/base_operation.py::BaseOperation` | 迁移操作基类 |
| `Operations.java` | `operation/sql_operations.py + vector_operations.py + kv_operations.py` | 8种操作类型合并 |
| `OperationRegistry.java` | `operation/operation_registry.py` | 操作注册表 |
| `MigrationPlan.java` | `migration_plan.py` | 全局迁移注册表（SQL/Vector/KV） |
| `MemoryMetaManager.java` | `migrator/memory_meta_manager.py` | memory_meta表CRUD |
| `SqlMigrator.java` | `migrator/sql_migrator.py` | SQL DDL迁移器（JDBC实现） |
| `VectorMigrator.java` | `migrator/vector_migrator.py` | 向量迁移器（受限实现） |
| `KvMigrator.java` | `migrator/kv_migrator.py` | KV数据迁移器（含备份恢复） |
| `RunMigrations.java` | `run_migrations.py` | 迁移入口 |

### 2.9 提取处理包（`process.extract`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `ExtractMemoryParams.java` | `extract/common.py` | 提取参数 |
| `VariableResult.java` | `extract/memory_analyzer.py::VariableResult` | 变量提取结果 |
| `MemoryAnalyzerResult.java` | `extract/memory_analyzer.py::MemoryAnalyzerResult` | 分析结果 |
| `MemoryAnalyzer.java` | `extract/memory_analyzer.py` | LLM驱动的记忆分析器 |
| `LongTermMemoryExtractor.java` | `extract/long_term_memory_extractor.py` | 长期记忆提取器 |
| `Generator.java` | `extract/generation.py` | 记忆生成编排器 |

### 2.10 提示词包（`prompt`）

| Java文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `PromptApplier.java` | `prompt/prompt_applier.py` | 提示词模板加载器（单例，类路径资源） |

### 2.11 资源文件

| 资源文件 | Python对应文件 | 说明 |
|----------|---------------|------|
| `resources/memory/prompt/fragment_memory_prompt.md` | `prompt/fragment_memory_prompt.md` | 片段记忆提取提示词 |
| `resources/memory/prompt/memory_analysis_prompt.md` | `prompt/memory_analysis_prompt.md` | 记忆分析提示词 |
| `resources/memory/prompt/memory_update_check.md` | `prompt/memory_update_check.md` | 记忆更新检查提示词 |

### 2.12 修改的已有文件

| 文件 | 修改内容 |
|------|---------|
| `StatusCode.java` | 新增3个错误码：MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR(158009)、MEMORY_REGISTER_OPERATION_VALIDATION_INVALID(158010)、MEMORY_INIT_ERROR(158011) |

## 3. 关键转译策略

### 3.1 异步→同步

Python的 `async/await` 全部转为Java同步方法。Java 21虚拟线程模型下，阻塞IO不会阻塞平台线程。

### 3.2 Pydantic→Lombok

Python Pydantic模型转为Java Lombok POJO：
- `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- 继承关系使用 `@SuperBuilder`
- `Field(default=...)` → `@Builder.Default`

### 3.3 SQLAlchemy/Alembic→JDBC

- `SqlDbStore` 使用纯JDBC实现（PreparedStatement参数化查询防注入）
- `SqlMigrator` 用JDBC DDL替代Alembic迁移
- `DbModel` 使用JDBC语句建表

### 3.4 AES-GCM加密

Python PyCryptodome → Java BouncyCastle（`GCMBlockCipher` + `AESEngine`）

### 3.5 单例模式

Python `metaclass=Singleton` → Java `volatile` + `synchronized` 双重检查锁定

### 3.6 Embedding集成

Python `BaseVectorStore` 内部处理embedding，Java `VectorStore` 接口要求预计算向量。解决方案：`SemanticStore` 持有 `Embedding` 引用，在 `addDocs()` 和 `search()` 方法中内部调用embedding。

### 3.7 锁机制

Python `async with lock` → Java `try-with-resources`（`DistributedLock implements AutoCloseable`）

### 3.8 配置加解密

Python直接修改不可变配置对象的字段 → Java通过JSON Map中间层进行API Key的加解密操作，避免直接修改不可变的 `ModelClientConfig` 对象。

## 4. 已知问题与限制

### 4.1 VectorMigrator 功能受限（严重）

**问题**：Java的 `VectorStore` 接口缺少以下Python `BaseVectorStore` 方法：
- `list_collection_names()` —— 无法列出所有集合
- `create_collection()` —— 无法显式创建集合（Java依赖add时自动创建）
- `update_schema()` —— 无法更新集合Schema
- `get_collection_metadata()` / `update_collection_metadata()` —— 无法读写集合元数据

**影响**：`VectorMigrator.tryMigrate()` 目前是空操作（no-op），向量Schema迁移实际不会执行。

**建议**：扩展Java `VectorStore` 接口，增加集合管理和Schema变更方法。

### 4.2 APIEmbedding 未实现（中等）

**问题**：Python中 `APIEmbedding(config=EmbeddingConfig)` 可以根据配置动态创建Embedding模型。Java没有对应的 `APIEmbedding` 工厂实现。

**影响**：`LongTermMemory` 中 scope级别的Embedding模型动态创建不生效，只能使用 `registerStore` 时传入的全局Embedding模型。

**建议**：实现Java版 `APIEmbedding` 类，支持从 `EmbeddingConfig` 动态创建Embedding实例。

### 4.3 SQLite ALTER COLUMN 限制（低）

**问题**：SQLite不支持 `ALTER COLUMN` 操作。`SqlMigrator` 中的 `RenameColumnOperation` 和 `UpdateColumnTypeOperation` 在SQLite上会失败。

**影响**：仅影响使用SQLite作为数据库后端的场景。MySQL和PostgreSQL正常支持。

### 4.4 PromptTemplate.format 参数类型（低）

**问题**：Java `PromptTemplate.format()` 接受 `Map<String,Object>`，Python版本接受 `Dict[str,str]`。当前实现按Java API传入 `Map<String,Object>`，`Object` 值会通过 `toString()` 转换。

**影响**：对于布尔值等非字符串类型的模板变量，输出格式可能略有不同（如Python `True` vs Java `true`）。

### 4.5 ModelClientConfig 不可变性（低）

**问题**：Java `ModelClientConfig` 使用手动Builder模式，字段为final，无setter方法。Python代码直接修改 `config.api_key` 属性。

**影响**：scope配置的API Key加解密通过JSON Map中间层处理，增加一次序列化/反序列化开销。功能正确，性能略有影响。

### 4.6 编译验证（待确认）

本次转译未执行编译验证（Maven build），可能存在以下类型的编译错误：
- 导入路径不匹配
- 方法签名不完全一致
- 泛型类型推断问题

**建议**：执行 `mvn compile` 进行编译验证，根据编译错误逐一修复。

## 5. 统计

| 指标 | 数量 |
|------|------|
| 新建Java文件 | 53 |
| 新建资源文件 | 3 |
| 修改已有文件 | 1 |
| Python对应文件 | ~50 |
| 总代码行数（估） | ~5000行 |
| 包数量 | 10 |

## 6. 包结构

```
com.openjiuwen.core.memory
├── LongTermMemory.java          # 主入口
├── MemInfo.java                 # 记忆信息
├── MemResult.java               # 搜索结果
├── common/
│   ├── DistributedLock.java
│   ├── KvPrefixRegistry.java
│   ├── MemoryCrypto.java
│   └── MemoryUtils.java
├── config/
│   ├── AgentMemoryConfig.java
│   ├── MemoryEngineConfig.java
│   └── MemoryScopeConfig.java
├── manage/
│   ├── index/
│   │   ├── BaseMemoryManager.java
│   │   ├── FragmentMemoryManager.java
│   │   ├── SummaryManager.java
│   │   ├── VariableManager.java
│   │   └── WriteManager.java
│   ├── mem_model/
│   │   ├── BaseMemoryUnit.java
│   │   ├── DataIdManager.java
│   │   ├── DbModel.java
│   │   ├── FragmentMemoryUnit.java
│   │   ├── MemoryType.java
│   │   ├── MessageAddRequest.java
│   │   ├── MessageManager.java
│   │   ├── ScopeUserMappingManager.java
│   │   ├── SemanticStore.java
│   │   ├── SqlDbStore.java
│   │   ├── SummaryUnit.java
│   │   ├── SupportMemoryType.java
│   │   ├── UserMemStore.java
│   │   └── VariableUnit.java
│   ├── search/
│   │   ├── SearchManager.java
│   │   └── SearchParams.java
│   └── update/
│       ├── CheckResult.java
│       ├── MemCheckItem.java
│       ├── MemoryActionItem.java
│       ├── MemoryStatus.java
│       └── MemUpdateChecker.java
├── migration/
│   ├── MigrationPlan.java
│   ├── RunMigrations.java
│   ├── migrator/
│   │   ├── KvMigrator.java
│   │   ├── MemoryMetaManager.java
│   │   ├── SqlMigrator.java
│   │   └── VectorMigrator.java
│   └── operation/
│       ├── BaseOperation.java
│       ├── OperationMetadata.java
│       ├── OperationRegistry.java
│       └── Operations.java
├── process/
│   └── extract/
│       ├── ExtractMemoryParams.java
│       ├── Generator.java
│       ├── LongTermMemoryExtractor.java
│       ├── MemoryAnalyzer.java
│       ├── MemoryAnalyzerResult.java
│       └── VariableResult.java
└── prompt/
    └── PromptApplier.java

resources/memory/prompt/
├── fragment_memory_prompt.md
├── memory_analysis_prompt.md
└── memory_update_check.md
```
