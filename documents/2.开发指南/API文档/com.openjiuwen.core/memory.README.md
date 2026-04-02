# memory

`com.openjiuwen.core.memory` groups the long-term memory engine, config models, manager implementations, extraction helpers, migration utilities, and prompt application APIs.

## Modules

| Module | Description |
| --- | --- |
| [`common`](./memory/common.README.md) | provides shared locking, crypto, prefix, and parsing helpers used across the memory engine. |
| [`config`](./memory/config.README.md) | defines system-wide, scope-level, and agent-level configuration models for memory behavior. |
| [`manage`](./memory/manage.README.md) | groups the manager subpackages that persist, search, and update memory state. |
| [`migration`](./memory/migration.README.md) | provides migration plans and entry points for KV, SQL, and vector memory stores. |
| [`process`](./memory/process.README.md) | groups the processing helpers that transform conversation history into structured memory artifacts. |
| [`prompt`](./memory/prompt.README.md) | provides prompt-template lookup and variable substitution utilities for memory workflows. |

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`LongTermMemory`](./memory/LongTermMemory.md) | class | Main memory engine implementing long-term memory management. |
| [`MemInfo`](./memory/MemInfo.md) | class | Memory information containing id, content, and type. |
| [`MemResult`](./memory/MemResult.md) | class | Memory search result with relevance score. |

## Notes

- This package page exposes the documented child packages for the current memory subtree.
- The current page also links the 3 direct public type page(s) defined in this package.
