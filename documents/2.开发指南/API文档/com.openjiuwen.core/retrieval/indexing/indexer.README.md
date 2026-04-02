# indexer

`com.openjiuwen.core.retrieval.indexing.indexer` contains index backend contracts and concrete indexers for in-memory, Milvus, and Chroma-style indexing.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`ChromaIndexer`](./indexer/ChromaIndexer.md) | `class` | Chroma-compatible indexer backed by the in-memory implementation. |
| [`InMemoryIndexer`](./indexer/InMemoryIndexer.md) | `class` | In-memory index manager backed by VectorStore. |
| [`IndexBackendConfig`](./indexer/IndexBackendConfig.md) | `interface` | Shared config surface that must match between vector store and index manager. |
| [`Indexer`](./indexer/Indexer.md) | `interface` | Index manager abstraction. |
| [`IndexerFactory`](./indexer/IndexerFactory.md) | `class` | Factory for pairing a vector store with its index manager implementation. |
| [`MilvusIndexer`](./indexer/MilvusIndexer.md) | `class` | Milvus-backed index manager for retrieval. |

## Notes

- The current page also links the 6 direct public type page(s) defined in this package.
