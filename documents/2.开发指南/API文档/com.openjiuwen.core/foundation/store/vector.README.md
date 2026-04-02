# vector

`com.openjiuwen.core.foundation.store.vector` contains vector-store adapters backed by the retrieval layer plus schema-migration helpers.

## Core Types

| Type | Description |
| --- | --- |
| [`ChromaVectorStore`](vector/ChromaVectorStore.md) | Foundation-store Chroma adapter. |
| [`InMemoryVectorStore`](vector/InMemoryVectorStore.md) | Foundation-store in-memory vector store. |
| [`MilvusVectorStore`](vector/MilvusVectorStore.md) | Foundation-store Milvus adapter. |
| [`PGVectorStore`](vector/PGVectorStore.md) | Foundation-store PGVector adapter. |
| [`VectorStoreUtils`](vector/VectorStoreUtils.md) | Conversion functions for Vector Store distance/similarity scores to normalized similarity [0, 1], and schema migration helpers. |

## Notes

- `AbstractRetrievalVectorStoreAdapter` stays internal to the package; the public docs focus on the concrete adapters exposed through `StoreFactory`.
