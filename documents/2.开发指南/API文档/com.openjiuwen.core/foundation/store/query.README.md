# query

`com.openjiuwen.core.foundation.store.query` registers and exposes the built-in query dialects for supported vector-store backends.

## Core Types

| Type | Description |
| --- | --- |
| [`ChromaQueryDialect`](query/ChromaQueryDialect.md) | Query expression support for ChromaDB. |
| [`MilvusQueryDialect`](query/MilvusQueryDialect.md) | Query expression support for Milvus. |
| [`QueryDialectRegistration`](query/QueryDialectRegistration.md) | Registers built-in query dialect implementations for Milvus and Chroma. |

## Notes

- `QueryDialectRegistration.ensureRegistered()` is idempotent and can be called during application startup without repeated registrations.
