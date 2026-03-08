# Retrieval 模块 API 文档

> 包路径：`com.openjiuwen.core.retrieval`

Retrieval 模块提供知识库构建、文档切分、向量化、索引、检索、查询改写、图扩展与结果融合等能力，适用于标准 RAG 和图增强检索场景。

---

## 目录

- [1. 核心入口](#1-核心入口)
- [2. 通用模型与配置](#2-通用模型与配置)
- [3. Embedding、索引与存储](#3-embedding索引与存储)
- [4. 检索与查询增强](#4-检索与查询增强)
- [5. 工具与辅助类](#5-工具与辅助类)

---

## 1. 核心入口

### 1.1 KnowledgeBase

知识库抽象基类，负责组织解析器、切分器、向量存储、索引器、检索器等组件。

**包路径**：`com.openjiuwen.core.retrieval`

**核心组件字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `config` | `KnowledgeBaseConfig` | 知识库配置 |
| `vectorStore` | `VectorStore` | 向量存储 |
| `embedModel` | `Embedding` | 向量模型 |
| `parser` | `Parser` | 文档解析器 |
| `chunker` | `Chunker` | 文本切分器 |
| `extractor` | `Extractor` | 三元组提取器 |
| `indexManager` | `Indexer` | 索引管理器 |
| `llmClient` | `BaseModelClient` | LLM 客户端 |
| `retriever` | `Retriever` | 检索器 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getConfig()` | `KnowledgeBaseConfig` | 获取知识库配置 |
| `getVectorStore()` / `setVectorStore(VectorStore vectorStore)` | `VectorStore` / `void` | 读写向量存储 |
| `getEmbedModel()` / `setEmbedModel(Embedding embedModel)` | `Embedding` / `void` | 读写嵌入模型 |
| `getParser()` / `setParser(Parser parser)` | `Parser` / `void` | 读写解析器 |
| `getChunker()` / `setChunker(Chunker chunker)` | `Chunker` / `void` | 读写切分器 |
| `getExtractor()` / `setExtractor(Extractor extractor)` | `Extractor` / `void` | 读写三元组提取器 |
| `getIndexManager()` / `setIndexManager(Indexer indexManager)` | `Indexer` / `void` | 读写索引管理器 |
| `getLlmClient()` / `setLlmClient(BaseModelClient llmClient)` | `BaseModelClient` / `void` | 读写 LLM 客户端 |
| `getRetriever()` / `setRetriever(Retriever retriever)` | `Retriever` / `void` | 读写检索器 |
| `parseFiles(List<String> filePaths)` | `List<Document>` | 解析文件列表 |
| `parseUrls(List<String> urls)` | `List<Document>` | 复用 `parseFiles()` 解析 URL 列表 |
| `addDocuments(List<Document> documents)` | `List<String>` | 抽象方法，写入知识库 |
| `retrieve(String query, RetrievalConfig config)` | `List<RetrievalResult>` | 抽象方法，检索知识 |
| `deleteDocuments(List<String> docIds)` | `boolean` | 抽象方法，删除文档 |
| `updateDocuments(List<Document> documents)` | `List<String>` | 抽象方法，更新文档 |
| `getStatistics()` | `Map<String, Object>` | 抽象方法，返回统计信息 |
| `close()` | `void` | 关闭 `retriever`、`vectorStore`、`indexManager` |

`KnowledgeBase` 会在运行时校验 `vectorStore` 与 `indexManager` 的后端配置是否一致，包括 `database_name`、`distance_metric`、`text_field`、`vector_field`、`metadata_field`、`doc_id_field` 等字段。

### 1.2 SimpleKnowledgeBase

标准的分块式知识库实现。

**包路径**：`com.openjiuwen.core.retrieval`

**构造方法**：
```java
SimpleKnowledgeBase(KnowledgeBaseConfig config)
SimpleKnowledgeBase(
    KnowledgeBaseConfig config,
    VectorStore vectorStore,
    Embedding embedModel,
    Parser parser,
    Chunker chunker,
    Indexer indexManager,
    BaseModelClient llmClient,
    Retriever retriever
)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addDocuments(List<Document> documents)` | `List<String>` | 切分文档并构建 chunk 索引 |
| `retrieve(String query, RetrievalConfig retrievalConfig)` | `List<RetrievalResult>` | 检索文档块 |
| `deleteDocuments(List<String> docIds)` | `boolean` | 删除指定文档对应 chunk |
| `updateDocuments(List<Document> documents)` | `List<String>` | 更新指定文档 |
| `getStatistics()` | `Map<String, Object>` | 返回索引存在性与索引信息 |
| `retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | `List<RetrievalResult>` | 多知识库聚合检索 |
| `retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | `List<MultiKBRetrievalResult>` | 多知识库聚合并保留来源 KB |

**默认检索器选择规则**：

| `KnowledgeBaseConfig.indexType` | 默认检索器 | 默认 mode |
|-------------------------------|------------|----------|
| `vector` | `VectorRetriever` | `vector` |
| `bm25` | `SparseRetriever` | `sparse` |
| `hybrid` | `HybridRetriever` | `hybrid` |

当 `RetrievalConfig.agentic=true` 时，会自动用 `AgenticRetriever` 包装默认检索器，此时要求 `llmClient` 已配置。

### 1.3 GraphKnowledgeBase

支持图检索的知识库实现，可同时维护 chunk 索引与 triple 索引。

**包路径**：`com.openjiuwen.core.retrieval`

**构造方法**：
```java
GraphKnowledgeBase(KnowledgeBaseConfig config)
GraphKnowledgeBase(
    KnowledgeBaseConfig config,
    VectorStore vectorStore,
    Embedding embedModel,
    Parser parser,
    Chunker chunker,
    Extractor extractor,
    Indexer indexManager,
    BaseModelClient llmClient,
    Retriever chunkRetriever,
    Retriever tripleRetriever
)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addDocuments(List<Document> documents)` | `List<String>` | 构建 chunk 索引；`useGraph=true` 时额外构建 triple 索引 |
| `retrieve(String query, RetrievalConfig retrievalConfig)` | `List<RetrievalResult>` | 根据配置选择普通检索或图检索 |
| `deleteDocuments(List<String> docIds)` | `boolean` | 删除 chunk 索引和 triple 索引中的数据 |
| `updateDocuments(List<Document> documents)` | `List<String>` | 删除旧数据后重新导入 |
| `getStatistics()` | `Map<String, Object>` | 返回 chunk/triple 索引统计 |
| `close()` | `void` | 关闭图检索器与父类资源 |

**图索引行为**：

- `KnowledgeBaseConfig.useGraph=true` 时启用 triple 索引。
- 未显式注入 `extractor` 且已提供 `llmClient` 时，会自动使用 `LLMTripleExtractor`。
- 检索时若 `RetrievalConfig.useGraph=false`，会退化为 `SimpleKnowledgeBase` 路径。

---

## 2. 通用模型与配置

### 2.1 文档与结果模型

#### Document

基础文档模型。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 文档 ID，默认自动生成 UUID |
| `text` | `String` | 文本内容，不能为空 |
| `metadata` | `Map<String, Object>` | 元数据，默认为空映射 |

**构造方法**：
```java
Document()
Document(String text)
Document(String id, String text)
Document(String id, String text, Map<String, Object> metadata)
```

#### MultimodalDocument

多模态文档，继承 `Document`，支持 `text`、`image`、`audio`、`video` 四类内容。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getContent()` | `List<Map<String, Object>>` | 返回多模态内容列表 |
| `addField(String kind, String data)` | `MultimodalDocument` | 直接添加内联数据 |
| `addField(String kind, Object data, Object filePath, Object dataId)` | `MultimodalDocument` | 从数据或文件路径添加内容 |
| `addField(String kind, Path filePath)` | `MultimodalDocument` | 从文件路径添加内容 |

#### TextChunk

文档切块模型。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | chunk ID |
| `text` | `String` | chunk 文本 |
| `docId` | `String` | 所属文档 ID |
| `metadata` | `Map<String, Object>` | 元数据 |
| `embedding` | `List<Float>` | 向量，可为空 |

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fromDocument(Document document, String chunkText)` | `TextChunk` | 由文档生成 chunk |
| `fromDocument(Document document, String chunkText, String id)` | `TextChunk` | 指定 chunk ID |

#### Triple

知识三元组模型。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 字段 | 类型 | 说明 |
|------|------|------|
| `subject` | `String` | 主体 |
| `predicate` | `String` | 谓词 |
| `object` | `String` | 宾语 |
| `confidence` | `Double` | 置信度，可为空 |
| `metadata` | `Map<String, Object>` | 元数据 |

#### SearchResult / RetrievalResult / MultiKBRetrievalResult

搜索与检索结果模型。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 核心字段 | 说明 |
|------|----------|------|
| `SearchResult` | `id`、`text`、`score`、`metadata` | 向量库原始返回结果 |
| `RetrievalResult` | `text`、`score`、`metadata`、`docId`、`chunkId` | 面向上层使用的检索结果 |
| `MultiKBRetrievalResult` | 继承 `RetrievalResult`，新增 `rawScore`、`rawScoreScaled`、`kbIds` | 多知识库聚合检索结果 |

#### TripleBeam / TripleMemory

图检索辅助结构。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 说明 |
|------|------|
| `TripleBeam` | 三元组 Beam，支持 `get(int index)`、`size()`、`contains()`、`iterator()` |
| `TripleMemory` | 去重三元组记忆，支持 `size()`、`getMemory()`、`getTriplesStr()`、`extendMemory()`、`batchExtendMemory()` |

### 2.2 配置模型

#### KnowledgeBaseConfig

知识库配置。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `kbId` | `String` | - | 知识库唯一标识 |
| `indexType` | `String` | `hybrid` | 索引类型，仅支持 `hybrid` / `bm25` / `vector` |
| `useGraph` | `boolean` | `false` | 是否启用图检索 |
| `chunkSize` | `int` | `512` | 切块大小 |
| `chunkOverlap` | `int` | `50` | 切块重叠 |

`validate()` 会校验 `kbId` 非空、`indexType` 合法、`chunkSize > 0`、`chunkOverlap >= 0`。

#### RetrievalConfig

检索时配置。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `topK` | `int` | `5` | 返回结果数 |
| `scoreThreshold` | `Double` | `null` | 分数阈值，仅部分模式支持 |
| `useGraph` | `Boolean` | `null` | 是否覆盖知识库默认图配置 |
| `agentic` | `boolean` | `false` | 是否启用 Agentic 检索 |
| `graphExpansion` | `boolean` | `false` | 是否启用图扩展 |
| `filters` | `Map<String, Object>` | `null` | 向量库过滤条件 |

#### VectorStoreConfig / IndexConfig / EmbeddingConfig / RerankerConfig

常用配置类。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 核心字段 | 说明 |
|------|----------|------|
| `VectorStoreConfig` | `storeProvider`、`databaseName`、`collectionName`、`distanceMetric` | 向量存储配置 |
| `IndexConfig` | `indexName`、`indexType` | 索引配置 |
| `EmbeddingConfig` | `modelName`、`baseUrl`、`apiKey` | Embedding 服务配置 |
| `RerankerConfig` | `apiKey`、`apiBase`、`modelName`、`timeout`、`temperature`、`topP`、`yesNoIds`、`extraBody` | 重排模型配置 |

### 2.3 排名配置与校验

#### BaseRankConfig / RRFRankConfig / WeightedRankConfig

结果融合配置模型。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 说明 |
|------|------|
| `BaseRankConfig` | 排名配置基类，定义 `getName()`、`isHigherIsBetter()`、`getArgs()`、`isActive()`、`getRankerClass()` |
| `RRFRankConfig` | RRF 配置，字段包括 `k`、`denseName`、`denseContent`、`sparseContent` |
| `WeightedRankConfig` | 加权融合配置，字段包括 `denseName`、`denseContent`、`sparseContent` |

#### ResultRankRegistry

数据库原生结果融合器注册表。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `registerResultRankerClass(String database, Class<?> weightedClass, Class<?> rrfClass, Map<String, Class<?>> extras)` | `void` | 注册数据库排名器实现 |
| `getRankerClass(String database, String name)` | `Class<?>` | 获取指定排名器实现 |
| `getRankerClasses(String database)` | `Map<String, Class<?>>` | 获取数据库下全部排名器 |

#### RetrievalValidation / RetrievalExceptions / StoreType

通用校验与异常辅助。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 说明 |
|------|------|
| `RetrievalValidation` | 提供索引类型、距离度量、存储类型与数据库名校验 |
| `RetrievalExceptions` | 提供 `error(StatusCode, String)` 与 `validation(String)` 两个快捷工厂 |
| `StoreType` | 支持的向量存储类型枚举：`MILVUS`、`CHROMA`、`PGVECTOR` |

### 2.4 回调与进度

#### BaseCallback / LoggingCallback

索引和嵌入阶段的回调接口。

**包路径**：`com.openjiuwen.core.retrieval.common`

| 类名 | 方法 | 说明 |
|------|------|------|
| `BaseCallback` | `onBatch(int startIdx, int endIdx, List<String> batch)`、`getCallCounter()` | 基础批处理回调 |
| `LoggingCallback` | `LoggingCallback(int total, String desc)`、重写 `onBatch(...)` | 基于 SLF4J 的进度日志回调 |

---

## 3. Embedding、索引与存储

### 3.1 Embedding

向量模型抽象接口。

**包路径**：`com.openjiuwen.core.retrieval.embedding`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `embedQuery(String text)` | `List<Float>` | 生成查询向量 |
| `embedDocuments(List<String> texts, Integer batchSize)` | `List<List<Float>>` | 批量生成文档向量 |
| `getDimension()` | `int` | 返回向量维度 |
| `getMaxBatchSize()` | `int` | 返回最大批大小，默认 `256` |

### 3.2 HashEmbedding / EmbeddingUtils

内置 Embedding 实现与工具方法。

**包路径**：`com.openjiuwen.core.retrieval.embedding`

| 类名 | 说明 |
|------|------|
| `HashEmbedding` | 基于 SHA-256 的本地确定性向量实现，构造方法为 `HashEmbedding()` 与 `HashEmbedding(int dimension, int maxBatchSize)` |
| `EmbeddingUtils` | 提供 `parseBase64Embedding(String base64Embedding)` |

### 3.3 VectorStore / InMemoryVectorStore

统一向量存储抽象及内存实现。

**包路径**：`com.openjiuwen.core.retrieval.vector_store`

#### VectorStore

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getCollectionName()` / `setCollectionName(String collectionName)` | `String` / `void` | 读写集合名 |
| `withCollection(String collectionName)` | `VectorStore` | 返回切换到指定集合的视图 |
| `add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | `void` | 写入数据 |
| `search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | 稠密检索 |
| `sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | 稀疏检索 |
| `hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | 混合检索 |
| `delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | `boolean` | 删除数据 |
| `tableExists(String tableName)` | `boolean` | 判断集合是否存在 |
| `deleteTable(String tableName)` | `void` | 删除集合 |
| `queryByFilters(Map<String, Object> filters, int limit)` | `List<SearchResult>` | 按元数据过滤查询 |
| `count(String tableName)` | `long` | 统计集合记录数 |

#### InMemoryVectorStore

本地内存向量库实现。

**构造方法**：
```java
InMemoryVectorStore(String collectionName)
InMemoryVectorStore(VectorStoreConfig config, String indexType)
```

支持：

- 稠密检索：`search(...)`
- BM25 风格稀疏检索：`sparseSearch(...)`
- 线性混合检索：`hybridSearch(...)`
- 按 `filters` 过滤、按 `collection` 隔离数据

### 3.4 Indexer / InMemoryIndexer

索引管理抽象与默认实现。

**包路径**：`com.openjiuwen.core.retrieval.indexing.indexer`

#### Indexer

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | 构建索引 |
| `updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | 按文档更新索引 |
| `deleteIndex(String docId, String indexName, Map<String, Object> options)` | `boolean` | 删除文档对应索引数据 |
| `indexExists(String indexName)` | `boolean` | 判断索引是否存在 |
| `getIndexInfo(String indexName)` | `Map<String, Object>` | 获取索引信息 |

#### InMemoryIndexer

基于 `VectorStore` 的默认索引器实现。

**构造方法**：
```java
InMemoryIndexer(VectorStore vectorStore)
```

行为要点：

- `buildIndex()` 与 `updateIndex()` 会自动将 `TextChunk` 转成向量库文档。
- 对 `vector` / `hybrid` 索引会批量调用 `Embedding.embedDocuments()`。
- `options.callback` 若为 `BaseCallback`，会在批处理嵌入时回调。

### 3.5 Processor、Parser、Chunker、Splitter、Extractor

索引流水线处理器抽象。

**包路径**：

- `com.openjiuwen.core.retrieval.indexing.processor`
- `com.openjiuwen.core.retrieval.indexing.processor.parser`
- `com.openjiuwen.core.retrieval.indexing.processor.chunker`
- `com.openjiuwen.core.retrieval.indexing.processor.splitter`
- `com.openjiuwen.core.retrieval.indexing.processor.extractor`

#### Processor\<I, O\>

```java
O process(I input, Map<String, Object> options)
```

#### Parser / TextFileParser

| 类名 | 说明 |
|------|------|
| `Parser` | 文档解析器抽象，核心方法是 `parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` |
| `TextFileParser` | UTF-8 文本解析器，支持 `.txt` 与 `.md` |

#### Chunker 及实现

| 类名 | 说明 |
|------|------|
| `Chunker` | 文本切块抽象，定义 `chunkText(String text)` 与 `chunkDocuments(List<Document> documents)` |
| `CharChunker` | 固定字符窗口切块 |
| `TokenizerChunker` | 基于 `SentenceSplitter` 的 tokenizer 感知切块 |
| `TextChunker` | 组合式切块器，内置空白归一化与 URL/Email 清洗 |
| `HybridChunker` | 在指定条件下对文档禁用切分 |
| `ChunkerRegistry` | 运行时注册/获取 Chunker 工厂 |
| `PreprocessingPipeline` | 文本预处理流水线 |
| `WhitespaceNormalizer` / `URLEmailRemover` / `SpecialCharacterNormalizer` | 内置预处理器 |

#### Splitter

| 类名 | 说明 |
|------|------|
| `Splitter` | 文本分割抽象，定义 `splitText(String text)` 与 `getNodesFromDocuments(List<Document> documents)` |
| `SentenceSplitter` | 句子级分割器，支持语言检测与 tokenizer 感知窗口 |

#### Extractor 及实现

| 类名 | 说明 |
|------|------|
| `Extractor` | 三元组提取抽象，定义 `extract(List<TextChunk> chunks, Map<String, Object> options)` |
| `SimpleTripleExtractor` | 基于句子和空白切分的本地三元组提取 |
| `LLMTripleExtractor` | 基于 LLM 的三元组提取器，支持并发执行 |

---

## 4. 检索与查询增强

### 4.1 Retriever

统一检索器抽象。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | 核心检索方法 |
| `batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | `List<List<RetrievalResult>>` | 批量检索 |
| `retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | `List<SearchResult>` | 原始搜索结果接口，默认未实现 |
| `retrieve(String query)` | `List<RetrievalResult>` | 默认 topK=5、mode=`hybrid` |
| `retrieve(String query, int topK)` | `List<RetrievalResult>` | 默认 mode=`hybrid` |
| `supportsMode(String mode)` | `boolean` | 是否支持某种 mode |
| `getIndexType()` | `String` | 返回索引类型，默认 `hybrid` |
| `close()` | `void` | 关闭资源，默认空实现 |

### 4.2 AbstractRetriever / AbstractStoreBackedRetriever

检索器基类。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

| 类名 | 说明 |
|------|------|
| `AbstractRetriever` | 提供 `batchRetrieve()` 默认实现 |
| `AbstractStoreBackedRetriever` | 为基于向量库的检索器提供 `vectorStore`、`embedModel` 与 `getIndexType()` |

### 4.3 VectorRetriever / SparseRetriever / HybridRetriever

基础检索器实现。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

| 类名 | 支持 mode | 说明 |
|------|-----------|------|
| `VectorRetriever` | `vector` | 稠密检索；若向量结果为空会回退到 `sparseSearch()` |
| `SparseRetriever` | `sparse` | 稀疏检索 / BM25 风格检索 |
| `HybridRetriever` | `hybrid`、`vector`、`sparse` | 混合检索器；`hybrid` 模式下使用 `alpha` 做线性融合 |

`HybridRetriever` 构造方法：
```java
HybridRetriever(VectorStore vectorStore, Embedding embedModel)
HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha)
```

### 4.4 GraphRetriever

图增强检索器。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

**构造方法**：
```java
GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever)
GraphRetriever(VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)
GraphRetriever(
    Retriever chunkRetriever,
    Retriever tripleRetriever,
    VectorStore vectorStore,
    Embedding embedModel,
    String chunkCollection,
    String tripleCollection
)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setIndexType(String indexType)` | `void` | 设置图检索器索引类型 |
| `getIndexType()` | `String` | 返回索引类型 |
| `supportsMode(String mode)` | `boolean` | 根据 `indexType` 校验 mode 合法性 |
| `getRetrieverForMode(String mode, boolean isChunk)` | `Retriever` | 获取 chunk 或 triple 检索器 |
| `retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | 图检索入口 |
| `graphExpansion(String query, List<RetrievalResult> chunks, List<RetrievalResult> triples, Integer topK, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | 对 chunk 结果做图扩展 |
| `close()` | `void` | 关闭注入的 chunk/triple retriever |

### 4.5 AgenticRetriever

带多轮改写和三元组阅读能力的检索器包装器。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

**构造方法**：
```java
AgenticRetriever(Retriever retriever, BaseModelClient llmClient)
AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `isGraphRetriever()` | `boolean` | 判断底层是否是 `GraphRetriever` |
| `getDefaultMode()` | `String` | 返回默认检索 mode |
| `getIndexType()` | `String` | 透传底层索引类型 |
| `retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | 多轮 Agentic 检索 |
| `batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | `List<List<RetrievalResult>>` | 批量 Agentic 检索 |
| `close()` | `void` | 关闭底层 retriever |

### 4.6 TripleBeamSearch

图扩展路径搜索器。

**包路径**：`com.openjiuwen.core.retrieval.retriever`

**构造方法**：
```java
TripleBeamSearch(Retriever retriever)
TripleBeamSearch(Retriever retriever, int numBeams, int numCandidatesPerBeam, int maxLength)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `beamSearch(String query, List<RetrievalResult> triples)` | `List<TripleBeam>` | 从三元组集合中执行 beam search |

要求底层 `retriever` 能提供 `Embedding` 能力，否则会抛错。

### 4.7 QueryRewriter

查询改写器，支持上下文压缩和模板加载。

**包路径**：`com.openjiuwen.core.retrieval.query_rewriter`

**构造方法**：
```java
QueryRewriter(BaseModelClient llmClient)
QueryRewriter(BaseModelClient llmClient, ModelContext context, int compressRange, String promptLang)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `rewrite(String query, List<RetrievalResult> results)` | `String` | 基于已有结果改写查询 |
| `compress(List<BaseMessage> messages)` | `Map<String, Object>` | 压缩历史消息 |
| `rewrite(String query)` | `Map<String, Object>` | 基于 `ModelContext` 做上下文感知改写 |
| `loadTemplate(String promptBase)` | `String` | 加载内置 Prompt 模板 |
| `msgToText(List<BaseMessage> messages)` | `String` | 将消息列表转成纯文本 |

### 4.8 Reranker / LexicalReranker

重排接口与内置实现。

**包路径**：`com.openjiuwen.core.retrieval.reranker`

| 类名 | 说明 |
|------|------|
| `Reranker` | 定义 `rerank(String query, List<RetrievalResult> candidates, int topK)` |
| `LexicalReranker` | 基于 token overlap 的本地重排实现 |

---

## 5. 工具与辅助类

### 5.1 ConfigManager

检索模块配置读写工具。

**包路径**：`com.openjiuwen.core.retrieval.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `ConfigManager()` / `ConfigManager(String configPath)` | - | 创建配置管理器 |
| `loadFromFile(String path)` | `void` | 从文件加载配置 |
| `saveToFile(String path)` | `void` | 保存配置到文件 |
| `getConfig(Class<T> configType)` | `T` | 按类型获取配置 |
| `getKnowledgeBaseConfig()` | `KnowledgeBaseConfig` | 获取知识库配置 |
| `updateConfig(Object config)` | `void` | 更新配置对象 |

### 5.2 CommonUtils / FusionUtils

检索结果工具方法。

**包路径**：`com.openjiuwen.core.retrieval.utils`

| 类名 | 方法 | 说明 |
|------|------|------|
| `CommonUtils` | `deduplicate(Iterable<T> data, Function<T, K> keyFn)` | 按 key 去重 |
| `FusionUtils` | `rrfFusionRetrieval(...)`、`rrfFusionSearch(...)`、`weightedFusionRetrieval(...)`、`weightedFusionSearch(...)` | 结果融合工具 |
