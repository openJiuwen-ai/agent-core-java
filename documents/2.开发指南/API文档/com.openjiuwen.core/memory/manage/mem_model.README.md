# mem_model

`com.openjiuwen.core.memory.manage.mem_model` defines the storage DTOs, adapters, and low-level models used by the memory managers.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`BaseMemoryUnit`](./mem_model/BaseMemoryUnit.md) | class | Base class for a single memory data item. |
| [`DataIdManager`](./mem_model/DataIdManager.md) | class | Generates unique memory IDs using timestamp + random + user hash. |
| [`DbModel`](./mem_model/DbModel.md) | class | Database model: table definitions and creation logic. |
| [`FragmentMemoryUnit`](./mem_model/FragmentMemoryUnit.md) | class | Fragment memory unit. |
| [`MemoryMeta`](./mem_model/MemoryMeta.md) | record | Public row model matching the memory_meta table. |
| [`MemoryType`](./mem_model/MemoryType.md) | enum | Types of memory data. |
| [`MessageAddRequest`](./mem_model/MessageAddRequest.md) | class | Request object for adding a message. |
| [`MessageManager`](./mem_model/MessageManager.md) | class | DB-based message management. |
| [`ScopeUserMapping`](./mem_model/ScopeUserMapping.md) | record | Public row model matching the memory scope_user_mapping table. |
| [`ScopeUserMappingManager`](./mem_model/ScopeUserMappingManager.md) | class | Manages scope-user mapping records in the SQL database. |
| [`SemanticStore`](./mem_model/SemanticStore.md) | class | Semantic store wrapping VectorStore for memory module. |
| [`SqlDbStore`](./mem_model/SqlDbStore.md) | class | JDBC-based SQL CRUD wrapper for memory tables. |
| [`SummaryUnit`](./mem_model/SummaryUnit.md) | class | Summary memory unit. |
| [`SupportMemoryType`](./mem_model/SupportMemoryType.md) | enum | Supported memory types for vector operations. |
| [`UserMemStore`](./mem_model/UserMemStore.md) | class | KV-based memory data storage with ID index management. |
| [`UserMessage`](./mem_model/UserMessage.md) | record | Public row model matching the memory user_message table. |
| [`VariableUnit`](./mem_model/VariableUnit.md) | class | Variable memory unit. |

## Notes

- The current page also links the 17 direct public type page(s) defined in this package.
