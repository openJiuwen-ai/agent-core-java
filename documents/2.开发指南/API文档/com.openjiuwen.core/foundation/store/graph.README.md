# graph

`com.openjiuwen.core.foundation.store.graph` contains graph-store interfaces, backend factories, configuration DTOs, and in-memory helpers.

## Core Types

| Type | Description |
| --- | --- |
| [`BM25Config`](graph/BM25Config.md) | Graph Database BM25 Options. |
| [`GraphConfig`](graph/GraphConfig.md) | Configuration of Graph Store. |
| [`GraphStore`](graph/GraphStore.md) | Interface defining the contract for graph vector store backends. |
| [`GraphStoreFactory`](graph/GraphStoreFactory.md) | Factory class to assemble graph store instances. |
| [`GraphStoreIndexConfig`](graph/GraphStoreIndexConfig.md) | Graph Database Indexing Options. |
| [`GraphStoreStorageConfig`](graph/GraphStoreStorageConfig.md) | Graph Database Storage Limits. |
| [`GraphUtils`](graph/GraphUtils.md) | Graph store utility functions. |
| [`InMemoryGraphStore`](graph/InMemoryGraphStore.md) | In-memory implementation of the foundation `GraphStore` contract. |
