# vector_store

`com.openjiuwen.core.retrieval.vector_store` contains vector-store contracts and concrete backends for in-memory, Chroma, Milvus, and PGVector storage.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`ChromaVectorStore`](./vector_store/ChromaVectorStore.md) | `class` | Local Chroma-compatible vector store backed by the in-memory implementation. |
| [`InMemoryVectorStore`](./vector_store/InMemoryVectorStore.md) | `class` | Local in-memory vector store used for translated retrieval regression tests. |
| [`MilvusVectorStore`](./vector_store/MilvusVectorStore.md) | `class` | Milvus-backed vector store for retrieval. |
| [`PGVectorStore`](./vector_store/PGVectorStore.md) | `class` | PostgreSQL/pgvector-backed vector store for retrieval. |
| [`SchemaMutableVectorStore`](./vector_store/SchemaMutableVectorStore.md) | `interface` | Optional extension for vector stores that support schema and collection metadata updates. |
| [`VectorStore`](./vector_store/VectorStore.md) | `interface` | Unified vector store abstraction. |
| [`VectorStoreFactory`](./vector_store/VectorStoreFactory.md) | `class` | Factory for creating vector stores from configuration. |

## Notes

- The current page also links the 7 direct public type page(s) defined in this package.
