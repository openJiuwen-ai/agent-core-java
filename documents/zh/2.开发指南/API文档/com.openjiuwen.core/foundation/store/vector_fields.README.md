# vector_fields

`com.openjiuwen.core.foundation.store.vector_fields` 定义面向不同后端的向量索引配置对象，以及默认 schema 辅助类。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`VectorField`](vector_fields/VectorField.md) | 向量索引配置抽象基类。 |
| [`BaseVectorFields`](vector_fields/BaseVectorFields.md) | 生成通用默认 schema。 |
| [`ChromaFields`](vector_fields/ChromaFields.md) | Chroma 默认 schema 入口。 |
| [`ChromaVectorField`](vector_fields/ChromaVectorField.md) | Chroma HNSW 索引配置。 |
| [`MilvusFields`](vector_fields/MilvusFields.md) | Milvus 默认 schema 入口。 |
| [`MilvusVectorField`](vector_fields/MilvusVectorField.md) | Milvus 索引配置抽象基类。 |
| [`MilvusAUTO`](vector_fields/MilvusAUTO.md) | Milvus AUTO 索引配置。 |
| [`MilvusFLAT`](vector_fields/MilvusFLAT.md) | Milvus FLAT 索引配置。 |
| [`MilvusHNSW`](vector_fields/MilvusHNSW.md) | Milvus HNSW 索引配置。 |
| [`MilvusIVF`](vector_fields/MilvusIVF.md) | Milvus IVF 索引配置。 |
| [`MilvusSCANN`](vector_fields/MilvusSCANN.md) | Milvus SCANN 索引配置。 |
| [`PGVectorField`](vector_fields/PGVectorField.md) | PGVector 索引配置。 |
| [`PgFields`](vector_fields/PgFields.md) | PGVector 默认 schema 入口。 |

## 说明

- 默认向量字段名统一为 `embedding`，除非调用方显式覆盖。
- `toDict(String stage)` 一般按 `construct` 与 `search` 两个阶段输出后端参数。
