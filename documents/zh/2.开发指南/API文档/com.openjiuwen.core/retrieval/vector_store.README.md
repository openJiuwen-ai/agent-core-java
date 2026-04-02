# vector_store

`com.openjiuwen.core.retrieval.vector_store` 定义 retrieval 子系统的统一向量库抽象，并提供内存、Milvus、PGVector 与兼容 Chroma 的实现，以及工厂与可选 schema 扩展接口。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`ChromaVectorStore`](./vector_store/ChromaVectorStore.md) | `class` | 基于 `InMemoryVectorStore` 的 Chroma 兼容实现。 |
| [`InMemoryVectorStore`](./vector_store/InMemoryVectorStore.md) | `class` | 面向本地回归测试的内存向量库实现。 |
| [`MilvusVectorStore`](./vector_store/MilvusVectorStore.md) | `class` | 基于 `MilvusClientV2` 的 Milvus 存储实现。 |
| [`PGVectorStore`](./vector_store/PGVectorStore.md) | `class` | 基于 PostgreSQL/pgvector 的存储实现。 |
| [`SchemaMutableVectorStore`](./vector_store/SchemaMutableVectorStore.md) | `interface` | 支持元数据和 schema 变更的可选扩展接口。 |
| [`VectorStore`](./vector_store/VectorStore.md) | `interface` | 统一向量库接口。 |
| [`VectorStoreFactory`](./vector_store/VectorStoreFactory.md) | `class` | 按 `VectorStoreConfig` 与 options 创建具体实现。 |

## 关键行为

- `InMemoryVectorStoreTest` 验证内存实现支持 BM25 风格 `sparseSearch(...)`、集合隔离、向量排序、混合检索、按 id 删除与按过滤条件查询。
- `MilvusVectorStoreTest` 验证 Milvus 实现会规范化 dense 分数、把过滤条件渲染为 Milvus filter 表达式，并在原生 hybrid 检索失败时回退到加权融合。
- `PGVectorStoreTest` 验证 PGVector 实现会在写入前自动建表建索引、规范化 dense 分数，并支持 weighted / RRF 混合融合。
- `VectorStoreFactoryTest` 验证工厂支持通过 `milvus_client`、`jdbcUrl`、`pg_uri`、`dataSource` 等 options 创建后端，并在缺失关键连接参数时抛异常。
