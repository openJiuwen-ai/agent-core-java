# retrieval

`com.openjiuwen.core.retrieval` 提供知识库抽象与基础实现，负责把 `VectorStore`、`Embedding`、`Parser`、`Chunker`、`Extractor`、`Indexer`、`BaseModelClient` 与 `Retriever` 组装成可执行的检索流程。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`common`](./retrieval/common.README.md) | 提供检索领域公共配置、数据模型、排序配置、异常与校验工具。 |
| [`embedding`](./retrieval/embedding.README.md) | 提供 embedding 抽象、HTTP embedding 客户端与本地哈希向量实现。 |
| [`indexing`](./retrieval/indexing.README.md) | 提供索引构建、分块、解析、抽取与索引后端实现。 |
| [`query_rewriter`](./retrieval/query_rewriter.README.md) | 提供基于 LLM 的查询改写与上下文压缩能力。 |
| [`reranker`](./retrieval/reranker.README.md) | 提供词法、远程 API 与 chat completion 三类重排器。 |
| [`retriever`](./retrieval/retriever.README.md) | 提供向量、稀疏、混合、图检索与 agentic 检索实现。 |
| [`utils`](./retrieval/utils.README.md) | 提供配置管理、融合排序、HTTP 请求与去重工具。 |
| [`vector_store`](./retrieval/vector_store.README.md) | 提供向量库抽象以及具体后端实现。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`KnowledgeBase`](./retrieval/KnowledgeBase.md) | 抽象知识库基类，统一装配、校验、解析、索引管理与关闭流程。 |
| [`SimpleKnowledgeBase`](./retrieval/SimpleKnowledgeBase.md) | 标准分块式知识库，支持向量、BM25 与混合检索。 |
| [`GraphKnowledgeBase`](./retrieval/GraphKnowledgeBase.md) | 在普通知识库基础上增加三元组抽取、图索引与图扩展检索。 |

## 关键行为

- `KnowledgeBase` 在构造时会校验 `KnowledgeBaseConfig`，并在 `vectorStore` 与 `indexManager` 同时存在时比对关键配置字段。
- `KnowledgeBase.parseUrls(...)` 会先调用 `parser.supports(url)`，仅解析当前 `Parser` 支持的 URL。
- `SimpleKnowledgeBase` 会根据 `KnowledgeBaseConfig.indexType` 自动选择 `vector`、`sparse` 或 `hybrid` 检索模式。
- `GraphKnowledgeBase` 在 `useGraph = true` 时会额外维护三元组索引；若缺少显式 `Extractor` 且存在 `llmClient`，会回退到 `LLMTripleExtractor`。
- 多知识库聚合检索会按文本去重，并保留最高得分结果及其来源知识库信息。

## 相关测试

- `KnowledgeBaseTest`
- `MilvusKnowledgeBaseTest`
- `PGVectorKnowledgeBaseTest`
- `RetrievalCoreTest`
