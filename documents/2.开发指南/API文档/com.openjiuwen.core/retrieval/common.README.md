# common

`com.openjiuwen.core.retrieval.common` contains shared DTOs, config models, callbacks, validation helpers, ranking settings, and retrieval result containers.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`BaseCallback`](./common/BaseCallback.md) | `class` | Base callback for indexing and embedding progress. |
| [`BaseRankConfig`](./common/BaseRankConfig.md) | `class` | Base type for result-ranker configuration. |
| [`Document`](./common/Document.md) | `class` | Document model. |
| [`EmbeddingConfig`](./common/EmbeddingConfig.md) | `class` | Embedding model configuration. |
| [`IndexConfig`](./common/IndexConfig.md) | `class` | Index configuration. |
| [`KnowledgeBaseConfig`](./common/KnowledgeBaseConfig.md) | `class` | Knowledge base configuration. |
| [`LoggingCallback`](./common/LoggingCallback.md) | `class` | Simple SLF4J-backed callback for batch progress. |
| [`MultiKBRetrievalResult`](./common/MultiKBRetrievalResult.md) | `class` | Retrieval result aggregated across multiple knowledge bases. |
| [`MultimodalDocument`](./common/MultimodalDocument.md) | `class` | Multimodal document model. |
| [`RRFRankConfig`](./common/RRFRankConfig.md) | `class` | RRF ranker configuration. |
| [`RerankerConfig`](./common/RerankerConfig.md) | `class` | Reranker model configuration aligned with the Python implementation. |
| [`ResultRankRegistry`](./common/ResultRankRegistry.md) | `class` | Registry for database-native ranker implementations. |
| [`RetrievalConfig`](./common/RetrievalConfig.md) | `class` | Retrieval-time options. |
| [`RetrievalExceptions`](./common/RetrievalExceptions.md) | `class` | Helpers for building retrieval-related exceptions with concise call sites. |
| [`RetrievalResult`](./common/RetrievalResult.md) | `class` | User-facing retrieval result. |
| [`RetrievalValidation`](./common/RetrievalValidation.md) | `class` | Shared retrieval validation helpers. |
| [`SearchResult`](./common/SearchResult.md) | `class` | Raw search result. |
| [`StoreType`](./common/StoreType.md) | `enum` | Supported vector store providers. |
| [`TextChunk`](./common/TextChunk.md) | `class` | Text chunk model. |
| [`TqdmCallback`](./common/TqdmCallback.md) | `class` | Lightweight progress callback aligned with Python's TqdmCallback. |
| [`Triple`](./common/Triple.md) | `class` | Knowledge triple. |
| [`TripleBeam`](./common/TripleBeam.md) | `class` | Beam of retrieval triples. |
| [`TripleMemory`](./common/TripleMemory.md) | `class` | Deduplicated triple memory. |
| [`VectorStoreConfig`](./common/VectorStoreConfig.md) | `class` | Vector store configuration. |
| [`WeightedRankConfig`](./common/WeightedRankConfig.md) | `class` | Weighted ranker configuration for dense/sparse fusion. |

## Notes

- The current page also links the 25 direct public type page(s) defined in this package.
