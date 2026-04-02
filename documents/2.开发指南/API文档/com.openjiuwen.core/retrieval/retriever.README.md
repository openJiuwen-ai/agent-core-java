# retriever

`com.openjiuwen.core.retrieval.retriever` contains dense, sparse, graph, hybrid, and agentic retriever implementations.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`AbstractRetriever`](./retriever/AbstractRetriever.md) | `class` | Common retriever defaults. |
| [`AbstractStoreBackedRetriever`](./retriever/AbstractStoreBackedRetriever.md) | `class` | Base class for retrievers backed by a vector store. |
| [`AgenticRetriever`](./retriever/AgenticRetriever.md) | `class` | Retriever that adds iterative query rewriting and triple reading on top of a base retriever. |
| [`GraphRetriever`](./retriever/GraphRetriever.md) | `class` | Graph-aware retriever that expands retrieved chunks through linked triples. |
| [`HybridRetriever`](./retriever/HybridRetriever.md) | `class` | Hybrid retriever combining sparse and dense retrieval. |
| [`Retriever`](./retriever/Retriever.md) | `interface` | Unified retriever abstraction. |
| [`SparseRetriever`](./retriever/SparseRetriever.md) | `class` | Sparse / BM25-like retriever. |
| [`TripleBeamSearch`](./retriever/TripleBeamSearch.md) | `class` | Triple beam search used by graph retrieval. |
| [`VectorRetriever`](./retriever/VectorRetriever.md) | `class` | Pure vector retriever. |

## Notes

- The current page also links the 9 direct public type page(s) defined in this package.
