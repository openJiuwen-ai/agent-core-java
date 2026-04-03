# retriever

`com.openjiuwen.core.retrieval.retriever` 提供统一检索器接口，以及向量检索、稀疏检索、混合检索、图扩展检索和代理式多轮检索的 Java 实现。

## 类型

| 类型 | 类别 | 说明 |
| --- | --- | --- |
| [`AbstractRetriever`](./retriever/AbstractRetriever.md) | `abstract class` | 提供 `batchRetrieve` 的默认串行实现。 |
| [`AbstractStoreBackedRetriever`](./retriever/AbstractStoreBackedRetriever.md) | `abstract class` | 为依赖 `VectorStore` 与 `Embedding` 的检索器暴露公共访问器。 |
| [`AgenticRetriever`](./retriever/AgenticRetriever.md) | `class` | 在基础检索器上叠加 LLM 读三元组与查询改写的多轮检索实现。 |
| [`GraphRetriever`](./retriever/GraphRetriever.md) | `class` | 支持 chunk/triple 双通路与图扩展的检索器。 |
| [`HybridRetriever`](./retriever/HybridRetriever.md) | `class` | 组合稠密向量检索与稀疏检索结果。 |
| [`Retriever`](./retriever/Retriever.md) | `interface` | 统一检索抽象，定义单条、批量与搜索结果转换接口。 |
| [`SparseRetriever`](./retriever/SparseRetriever.md) | `class` | 只支持 `sparse` 模式的稀疏检索器。 |
| [`TripleBeamSearch`](./retriever/TripleBeamSearch.md) | `class` | 图检索扩展阶段使用的三元组 beam search。 |
| [`VectorRetriever`](./retriever/VectorRetriever.md) | `class` | 只支持 `vector` 模式的向量检索器。 |

## 关键行为

- `Retriever` 默认将 `retrieve(String query)` 映射为 `topK = 5`、`mode = "hybrid"`、`options = Map.of()`。
- `RetrieverDefaultMethodTest` 验证 `retrieveSearchResults(...)` 会把 `RetrievalResult` 转换成 `SearchResult`，并按 `chunkId -> docId -> text.hashCode()` 的顺序回退生成结果 `id`。
- `GraphRetriever` 与 `AgenticRetriever` 都会依据底层索引类型推导允许的检索模式，并在模式不兼容或缺少依赖时抛出 retrieval 领域异常。
- `VectorRetriever`、`HybridRetriever` 在向量检索无结果时都会回退到 `VectorStore.sparseSearch(...)`。
