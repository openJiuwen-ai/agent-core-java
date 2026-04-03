# indexer

`com.openjiuwen.core.retrieval.indexing.indexer` 提供索引管理抽象，以及面向通用 `VectorStore` 与 Milvus 的具体实现。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`ChromaIndexer`](./indexer/ChromaIndexer.md) | `class` | 复用 `InMemoryIndexer` 的 Chroma 兼容索引器。 |
| [`InMemoryIndexer`](./indexer/InMemoryIndexer.md) | `class` | 基于 `VectorStore` 的通用内存/通用后端索引实现。 |
| [`IndexBackendConfig`](./indexer/IndexBackendConfig.md) | `interface` | 统一约束索引器与向量库需要暴露的后端字段名与索引配置。 |
| [`Indexer`](./indexer/Indexer.md) | `interface` | 索引构建、更新、删除和元信息查询的统一接口。 |
| [`IndexerFactory`](./indexer/IndexerFactory.md) | `class` | 按 `VectorStore` 类型选择 `Indexer` 实现。 |
| [`MilvusIndexer`](./indexer/MilvusIndexer.md) | `class` | 使用 `MilvusVectorStore` 和 `MilvusClientV2` 的 Milvus 专用索引器。 |

## 关键行为

- `InMemoryIndexer` 在 `indexType != "bm25"` 时要求必须提供 `Embedding`，否则抛出 `RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND`。
- `MilvusIndexer` 会在 `buildIndex(...)` 前调用 `ensureCollection(...)`，并在已存在相同 `doc_id` 时拒绝插入。
- 两个索引器都把 `TextChunk` 转成包含 `doc_id`、`chunk_id`、正文与 metadata 的写入文档；其中 `InMemoryIndexer` 还会显式写入 `id` 字段。
