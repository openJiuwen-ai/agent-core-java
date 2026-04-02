# vector_fields

`com.openjiuwen.core.foundation.store.vector_fields` defines ANN/vector-field configuration objects for Chroma, Milvus, and PGVector backends.

## Core Types

| Type | Description |
| --- | --- |
| [`BaseVectorFields`](vector_fields/BaseVectorFields.md) | Reusable helpers for building vector collection schemas. |
| [`ChromaFields`](vector_fields/ChromaFields.md) | Chroma-compatible field helpers. |
| [`ChromaVectorField`](vector_fields/ChromaVectorField.md) | HNSW index configuration for ChromaDB vector database. |
| [`MilvusAUTO`](vector_fields/MilvusAUTO.md) | AUTOINDEX configuration for Milvus. |
| [`MilvusFields`](vector_fields/MilvusFields.md) | Milvus-compatible field helpers. |
| [`MilvusFLAT`](vector_fields/MilvusFLAT.md) | FLAT index configuration for Milvus. |
| [`MilvusHNSW`](vector_fields/MilvusHNSW.md) | Hierarchical Navigable Small World (HNSW) index configuration for Milvus. |
| [`MilvusIVF`](vector_fields/MilvusIVF.md) | Inverted File (IVF) index configuration for Milvus. |
| [`MilvusSCANN`](vector_fields/MilvusSCANN.md) | SCANN (Scalable Nearest Neighbors) index configuration for Milvus. |
| [`MilvusVectorField`](vector_fields/MilvusVectorField.md) | Base class for Milvus vector field configurations. |
| [`PgFields`](vector_fields/PgFields.md) | PGVector-compatible field helpers. |
| [`PGVectorField`](vector_fields/PGVectorField.md) | Index configuration for PGVector database. |
| [`VectorField`](vector_fields/VectorField.md) | Base class for configuring Approximate Nearest Neighbor (ANN) search in vector databases. |
