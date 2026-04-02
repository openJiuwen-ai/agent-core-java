# retrieval

`com.openjiuwen.core.retrieval` groups knowledge-base entry points, shared configs, embeddings, rerankers, query rewriting, indexing, retrievers, vector stores, and helper utilities for retrieval workflows.

## Modules

| Module | Description |
| --- | --- |
| [`common`](./retrieval/common.README.md) | contains shared DTOs, config models, callbacks, validation helpers, ranking settings, and retrieval result containers. |
| [`embedding`](./retrieval/embedding.README.md) | contains embedding contracts, HTTP-backed embedding clients, and vector parsing helpers. |
| [`indexing`](./retrieval/indexing.README.md) | groups indexing backends and document-processing pipeline stages used to build searchable retrieval collections. |
| [`query_rewriter`](./retrieval/query_rewriter.README.md) | contains query rewriting utilities that use templates, LLM calls, and context compression before retrieval. |
| [`reranker`](./retrieval/reranker.README.md) | contains reranker contracts plus lexical, chat-based, and remote reranking implementations. |
| [`retriever`](./retrieval/retriever.README.md) | contains dense, sparse, graph, hybrid, and agentic retriever implementations. |
| [`utils`](./retrieval/utils.README.md) | contains HTTP, configuration, fusion, and general-purpose helpers shared by retrieval components. |
| [`vector_store`](./retrieval/vector_store.README.md) | contains vector-store contracts and concrete backends for in-memory, Chroma, Milvus, and PGVector storage. |

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`GraphKnowledgeBase`](./retrieval/GraphKnowledgeBase.md) | `class` | Knowledge base with optional graph index. |
| [`KnowledgeBase`](./retrieval/KnowledgeBase.md) | `class` | Abstract knowledge base. |
| [`SimpleKnowledgeBase`](./retrieval/SimpleKnowledgeBase.md) | `class` | Standard chunk-based knowledge base. |

## Notes

- This package page links the documented child packages in the current retrieval subtree.
- The current page also links the 3 direct public type page(s) defined in this package.
