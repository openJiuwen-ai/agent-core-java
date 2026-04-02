# store

`com.openjiuwen.core.foundation.store` collects vector, graph, key-value, database, and object-storage adapters used by the foundation layer.

## Modules

| Module | Description |
| --- | --- |
| [`base_embedding`](store/base_embedding.README.md) | embedding interface and configuration types shared by graph/vector backends. |
| [`db`](store/db.README.md) | database-engine holder used by storage integrations. |
| [`graph`](store/graph.README.md) | graph-store contracts, factories, and configuration DTOs. |
| [`kv`](store/kv.README.md) | in-memory and database-backed key-value stores. |
| [`object`](store/object.README.md) | local object-storage client for file-backed artifacts. |
| [`query`](store/query.README.md) | built-in query-dialect registrations for vector stores. |
| [`vector`](store/vector.README.md) | retrieval-backed vector-store adapters and schema helpers. |
| [`vector_fields`](store/vector_fields.README.md) | vector-index configuration objects for Chroma, Milvus, and PGVector. |

## Core Types

| Type | Description |
| --- | --- |
| [`StoreFactory`](store/StoreFactory.md) | Factory helpers for foundation.store concrete implementations. |

## Notes

- `StoreFactory` is the Java entry point for selecting the built-in vector-store adapters by short store type names.
- The graph, query, vector, and vector-field packages bridge the `foundation.store` API onto the retrieval and SPI layers.
