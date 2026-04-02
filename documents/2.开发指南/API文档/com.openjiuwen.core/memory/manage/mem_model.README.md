# mem_model

`com.openjiuwen.core.memory.manage.mem_model` 定义管理层使用的数据模型与底层存储适配器，包括记忆单元、SQL 行模型、枚举值、消息写入请求，以及向量/SQL/KV 封装。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`BaseMemoryUnit`](./mem_model/BaseMemoryUnit.md) | 单条记忆单元的抽象基类。 |
| [`DataIdManager`](./mem_model/DataIdManager.md) | 负责生成记忆与消息标识。 |
| [`DbModel`](./mem_model/DbModel.md) | 定义 SQL 表结构并负责初始化建表。 |
| [`FragmentMemoryUnit`](./mem_model/FragmentMemoryUnit.md) | 分片记忆单元。 |
| [`MemoryMeta`](./mem_model/MemoryMeta.md) | `memory_meta` 表的公开记录模型。 |
| [`MemoryType`](./mem_model/MemoryType.md) | 记忆类型枚举。 |
| [`MessageAddRequest`](./mem_model/MessageAddRequest.md) | 新增消息请求对象。 |
| [`MessageManager`](./mem_model/MessageManager.md) | 基于 SQL 的消息管理器。 |
| [`ScopeUserMapping`](./mem_model/ScopeUserMapping.md) | `scope_user_mapping` 表的公开记录模型。 |
| [`ScopeUserMappingManager`](./mem_model/ScopeUserMappingManager.md) | 管理用户与作用域映射记录。 |
| [`SemanticStore`](./mem_model/SemanticStore.md) | 向量存储适配器，封装嵌入初始化与搜索写入。 |
| [`SqlDbStore`](./mem_model/SqlDbStore.md) | JDBC 风格的 SQL CRUD 封装。 |
| [`SummaryUnit`](./mem_model/SummaryUnit.md) | 摘要记忆单元。 |
| [`SupportMemoryType`](./mem_model/SupportMemoryType.md) | 管理层支持的记忆类型枚举。 |
| [`UserMemStore`](./mem_model/UserMemStore.md) | 基于 KV 的用户记忆存储。 |
| [`UserMessage`](./mem_model/UserMessage.md) | 用户消息行记录模型。 |
| [`VariableUnit`](./mem_model/VariableUnit.md) | 变量记忆单元。 |

## 相关测试

- `DbModelTest`
- `SemanticStoreMilvusTest`
- `SemanticStorePGVectorTest`
- `SqlDbStoreTest`
