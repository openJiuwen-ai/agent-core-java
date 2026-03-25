# retrieval 模块转译报告

## 1. 任务背景

Python 版 `openjiuwen.core.retrieval` 在仓库中是完整模块，Java 版此前没有对应的 `com.openjiuwen.core.retrieval` 包，核心代码、知识库实现、检索器、公共数据模型和单元测试均缺失。

本次工作以“先保证主链路可运行、可测试，再补足模块分层”为原则完成转译。

## 2. 本次实际完成的转译范围

本次在 Java 版新增了 `59` 个 retrieval 相关源码文件，覆盖以下层次：

- `common`
  - `KnowledgeBaseConfig / RetrievalConfig / IndexConfig / VectorStoreConfig / EmbeddingConfig`
  - `Document / TextChunk / MultimodalDocument`
  - `SearchResult / RetrievalResult / MultiKBRetrievalResult`
  - `Triple / TripleBeam / TripleMemory`
  - 统一校验与异常辅助

- `embedding`
  - `Embedding`
  - `HashEmbedding`

- `indexing.indexer`
  - `IndexBackendConfig`
  - `Indexer`
  - `InMemoryIndexer`

- `indexing.processor`
  - `Processor`
  - `Parser / TextFileParser`
  - `Extractor / SimpleTripleExtractor`
  - `Splitter / SentenceSplitter`
  - `Chunker / CharChunker / TokenizerChunker / TextChunker / HybridChunker`
  - `ChunkerRegistry`
  - 文本预处理链

- `vector_store`
  - `VectorStore`
  - `InMemoryVectorStore`

- `retriever`
  - `Retriever`
  - `VectorRetriever / SparseRetriever / HybridRetriever`
  - `GraphRetriever / TripleBeamSearch`
  - `AgenticRetriever`

- 根层知识库
  - `KnowledgeBase`
  - `SimpleKnowledgeBase`
  - `GraphKnowledgeBase`

- `utils`
  - `CommonUtils`
  - `FusionUtils`
  - `ConfigManager`

- 补充层
  - `query_rewriter.QueryRewriter`
  - `reranker.Reranker`
  - `reranker.LexicalReranker`

## 3. 关键实现决策

### 3.1 先实现可运行后端，而不是伪造外部适配器

Python 版 retrieval 依赖了 Chroma、Milvus、PGVector、外部 embedding API、多种文件解析器等外部组件。Java 版当前没有这些驱动与依赖。

为了满足“不要用占位符假装实现”的要求，本次没有伪造这些外部适配器，而是新增了：

- `InMemoryVectorStore`
- `InMemoryIndexer`

这两个实现是完整可运行的本地后端，支持：

- 向量检索
- 稀疏检索
- 混合检索
- 过滤查询
- 索引增删改查
- 图检索所需的 `chunk_id / doc_id` 回查

这样 `retriever -> knowledge base -> UT` 整条链路都能真实执行。

### 3.2 将 Python 的异步接口转成 Java 的同步接口

Python retrieval 大量使用 `async/await`。Java 当前工程整体更偏同步调用模型，因此本次转译统一采用同步接口，但保留了 Python 侧的重要行为语义：

- mode 校验
- score_threshold 限制
- vector -> sparse fallback
- graph expansion
- agentic 多轮检索
- knowledge base 的 index compatibility 校验

### 3.3 图检索链路做了真实闭环

`GraphRetriever` 不是空壳，已完成：

- chunk retriever / triple retriever 动态选择
- graph mode 校验
- `graphExpansion`
- 基于 triple metadata 的 beam search
- triple 到 chunk 的回查
- 结果融合

为了支撑该链路，`SimpleTripleExtractor` 与 triple 索引构建时补齐了：

- `doc_id`
- `chunk_id`
- `triple` JSON 元数据

### 3.4 Agentic 检索已可运行

`AgenticRetriever` 已完成：

- generic retriever 多轮重写
- graph retriever 多轮 graph-expansion 流程
- `_read`
- `_rewrite`
- triple linking
- passage linking
- RRF 融合

LLM 调用通过现有 `BaseModelClient` 接口接入；测试中使用了可控的假实现进行验证。

## 4. 本次修复的问题

除新增 retrieval 模块外，本次还修复了以下实际问题：

### 4.1 Java 侧 retrieval 模块整体缺失

这是本次最核心的问题，已补齐主链路。

### 4.2 Python 的“必填字段”语义和 Java Bean 语义不一致

Python 版依赖 Pydantic，Java 侧则存在无参构造/配置装载场景。为兼顾：

- 运行时代码保留了 Java 友好的配置装载方式
- UT 转译时，对“缺少必填字段”的断言改为针对显式非法构造进行验证

### 4.3 `MultimodalDocument` 默认 `text=""` 的兼容问题

早期实现若把 `Document.text` 校验成“非空白”，会破坏 `MultimodalDocument` 的默认行为。已调整为和 Python 一致的“必填但允许空串”语义。

### 4.4 现有 session 测试阻塞 retrieval UT 运行

仓库里已有的 `StateTest` 在 `test-compile` 阶段因为：

- `WorkflowCommitState.createNodeState(String)` 缺少兼容重载

导致 Maven 无法编译测试代码，间接阻塞 retrieval UT 执行。

本次补了一个向后兼容重载：

- `WorkflowCommitState.createNodeState(String)`

该修改是为了解除测试编译阻塞，不影响 retrieval 逻辑。

## 5. 当前仍未完全对齐 Python 的部分

以下部分本次没有做成 Python 等价的外部依赖适配：

- `vector_store` 的 Chroma / Milvus / PGVector 真正驱动适配
- `embedding` 的 OpenAI / vLLM / APIEmbedding 远程实现
- parser 侧的 PDF / Word / Excel / Web / 图片等复杂解析器
- Python 版更完整的 query_rewriter / reranker 外部服务能力

目前 Java 版已经具备：

- retrieval 主链路
- 本地可运行后端
- knowledge base 闭环
- graph / agentic 行为回归

但外部服务型子模块仍需后续逐个接驱动或 HTTP client。

## 6. 结论

本次已经把 Java 版 retrieval 从“基本不存在”推进到“核心结构齐全、主链路可运行、知识库可用、单测可回归”的状态。

如果后续要继续追平 Python 版，建议按下面顺序追加：

1. 外部向量库驱动适配
2. 远程 embedding provider
3. 复杂 parser 适配
4. 更高保真度的 query_rewriter / reranker
