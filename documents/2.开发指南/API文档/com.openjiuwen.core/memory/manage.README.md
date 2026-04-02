# manage

`com.openjiuwen.core.memory.manage` groups the manager subpackages that persist, search, and update memory state.

## Modules

| Module | Description |
| --- | --- |
| [`index`](./manage/index.README.md) | contains manager implementations for fragment memory, summary memory, variable memory, and write orchestration. |
| [`mem_model`](./manage/mem_model.README.md) | defines the storage DTOs, adapters, and low-level models used by the memory managers. |
| [`search`](./manage/search.README.md) | contains the search request model and the search coordinator used by memory retrieval flows. |
| [`update`](./manage/update.README.md) | defines memory update actions, statuses, and conflict-check helpers before writes are applied. |

## Notes

- This package page exposes the documented child packages for the current memory subtree.
