# index

`com.openjiuwen.core.memory.manage.index` contains manager implementations for fragment memory, summary memory, variable memory, and write orchestration.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`BaseMemoryManager`](./index/BaseMemoryManager.md) | class | Abstract base class for memory manager implementations. |
| [`FragmentMemoryManager`](./index/FragmentMemoryManager.md) | class | Manages fragment (user profile) memory CRUD with encryption and vector storage. |
| [`SummaryManager`](./index/SummaryManager.md) | class | Manages summary memory CRUD with encryption and vector storage. |
| [`VariableManager`](./index/VariableManager.md) | class | Manages variable memory using KV store. |
| [`WriteManager`](./index/WriteManager.md) | class | Orchestrates memory write operations across all memory type managers. |

## Notes

- The current page also links the 5 direct public type page(s) defined in this package.
