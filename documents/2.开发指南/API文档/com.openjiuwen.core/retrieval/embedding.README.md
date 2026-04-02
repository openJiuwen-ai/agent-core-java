# embedding

`com.openjiuwen.core.retrieval.embedding` contains embedding contracts, HTTP-backed embedding clients, and vector parsing helpers.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`APIEmbedding`](./embedding/APIEmbedding.md) | `class` | Universal HTTP embedding client aligned with the Python APIEmbedding implementation. |
| [`Embedding`](./embedding/Embedding.md) | `interface` | Embedding model abstraction. |
| [`EmbeddingUtils`](./embedding/EmbeddingUtils.md) | `class` | Helpers for embedding model implementations. |
| [`HashEmbedding`](./embedding/HashEmbedding.md) | `class` | Deterministic local embedding based on SHA-256 hashing. |
| [`OpenAIEmbedding`](./embedding/OpenAIEmbedding.md) | `class` | OpenAI-compatible embedding client with base64 embedding support. |
| [`VLLMEmbedding`](./embedding/VLLMEmbedding.md) | `class` | vLLM-compatible multimodal embedding client. |

## Notes

- The current page also links the 6 direct public type page(s) defined in this package.
