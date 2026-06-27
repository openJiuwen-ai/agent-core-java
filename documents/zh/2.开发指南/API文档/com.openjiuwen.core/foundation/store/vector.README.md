# vector

`com.openjiuwen.core.foundation.store.vector` 提供 foundation 向量存储适配器与辅助工具，统一把 retrieval 层 `VectorStore` 暴露为 foundation 层接口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`InMemoryVectorStore`](vector/InMemoryVectorStore.md) | 内存向量存储适配器。 |
| [`ChromaVectorStore`](vector/ChromaVectorStore.md) | Chroma 向量存储适配器。 |
| [`MilvusVectorStore`](vector/MilvusVectorStore.md) | Milvus 向量存储适配器。 |
| [`PGVectorStore`](vector/PGVectorStore.md) | PGVector 向量存储适配器。 |
| [`VectorStoreUtils`](vector/VectorStoreUtils.md) | 分值转换、schema 迁移与文档变换工具。 |

## 说明

- 包内还有非 public 的 `AbstractRetrievalVectorStoreAdapter`，它是统一桥接 retrieval 层实现的核心适配基类。
- 各具体适配器主要差异在构造 retrieval 层 store 时读取的配置项与默认值。
