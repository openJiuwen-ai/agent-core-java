# reranker

`com.openjiuwen.core.retrieval.reranker` contains reranker contracts plus lexical, chat-based, and remote reranking implementations.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`ChatReranker`](./reranker/ChatReranker.md) | `class` | Chat-completion-based reranker aligned with Python's ChatReranker behavior. |
| [`LexicalReranker`](./reranker/LexicalReranker.md) | `class` | Local lexical reranker based on token overlap. |
| [`Reranker`](./reranker/Reranker.md) | `interface` | Reranker abstraction. |
| [`StandardReranker`](./reranker/StandardReranker.md) | `class` | Remote reranker implementation aligned with Python's StandardReranker behavior. |

## Notes

- The current page also links the 4 direct public type page(s) defined in this package.
