# retrieval 模块 Python↔Java API 映射复核

## 1. 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\retrieval`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\retrieval`
- 输出目标: 梳理 retrieval 模块各子包、类、公开方法的 Python↔Java 对位关系，并标出真实缺漏与仅属 Java 化适配的差异

## 2. 复核口径

以下差异默认不计入“缺漏”:

- `snake_case -> camelCase`
- `async def -> 同步方法`
- Python 模块函数收口为 Java `static` 工具类/工厂类/注册表
- `BaseModel`/属性模型 -> Java `POJO`/getter/setter/构造器
- Python 私有辅助函数被 Java 内联实现，但对外语义不变

## 3. 总体结论

- retrieval 模块主干已经完成 Java 化，顶层知识库、检索器、向量库、索引器、解析器、chunker、改写器、reranker、公共配置与数据模型基本都有可对位实现。
- 旧的缺漏报告中若干高优先级项已经不再成立，例如 `RerankerConfig`、`result_ranking`、`callbacks`、`LLMTripleExtractor`、`EmbeddingUtils.parseBase64Embedding`、`StoreType`、`hybrid` chunker 注册、`RetrievalConfig` 基本校验等，在 Java 侧均已存在。
- 当前仍需重点关注的真实差异，不再是“模块整体缺失”，而是若干公开辅助 API 和接口契约没有完全追平 Python，包括多 KB helper 的签名/返回值、`KnowledgeBase.delete_collection`、`VectorStore.check_vector_field` 契约、`Reranker` 接口返回形态、`TextSplitter` 家族等。

## 4. 文件、类、方法映射

### 4.1 顶层入口

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `knowledge_base.py::KnowledgeBase` | `KnowledgeBase` | `parse_files -> parseFiles`；`parse_urls -> parseUrls`；`add_documents -> addDocuments`；`retrieve -> retrieve`；`delete_documents -> deleteDocuments`；`update_documents -> updateDocuments`；`get_statistics -> getStatistics`；`close -> close` | 主入口对齐 |
| `knowledge_base.py::KnowledgeBase.__setattr__` + `validate_index` | `KnowledgeBase.setVectorStore/setIndexManager` + `validateIndex` | Python 自动赋值校验 -> Java setter/构造时显式校验 | 语义基本对齐，但触发机制不同 |
| `knowledge_base.py::delete_collection` | 无直接对位 | Python `delete_collection(collection)` | Java 顶层缺少公开对位 API |
| `simple_knowledge_base.py::SimpleKnowledgeBase` | `SimpleKnowledgeBase` | `add_documents -> addDocuments`；`retrieve -> retrieve`；`delete_documents -> deleteDocuments`；`update_documents -> updateDocuments`；`get_statistics -> getStatistics` | 核心知识库能力对齐 |
| `simple_knowledge_base.py::retrieve_multi_kb` | `SimpleKnowledgeBase.retrieveMultiKb` | Python: `List[str]` 文本列表；Java: `List<RetrievalResult>` | 仅部分对位，返回形态不同 |
| `simple_knowledge_base.py::retrieve_multi_kb_with_source` | `SimpleKnowledgeBase.retrieveMultiKbWithSource` | Python: `(kbs, query, config=None, top_k=None)`；Java: `(knowledgeBases, query, topK)` | 主能力存在，但缺少 `RetrievalConfig` 透传 |
| `graph_knowledge_base.py::GraphKnowledgeBase` | `GraphKnowledgeBase` | `add_documents -> addDocuments`；`retrieve -> retrieve`；`delete_documents -> deleteDocuments`；`update_documents -> updateDocuments`；`get_statistics -> getStatistics`；`close -> close` | 图知识库主流程对齐 |
| `graph_knowledge_base.py::retrieve_multi_graph_kb` | 无直接对位 | Python graph 多 KB helper | Java 缺少同名 helper |
| `graph_knowledge_base.py::retrieve_multi_graph_kb_with_source` | 无直接对位 | Python graph 多 KB helper | Java 缺少同名 helper |

补充说明:

- Python `KnowledgeBase` 公开了 `strict_validation` 构造参数；Java 当前没有等价公开开关。
- Java `KnowledgeBase` 额外提供 `get/setVectorStore`、`get/setParser`、`get/setChunker`、`get/setExtractor`、`get/setIndexManager`、`get/setRetriever` 等显式访问器。
- Java `KnowledgeBase` 通过 `resolveIndexManager()` + `IndexerFactory.createIndexer(...)` 自动补 index manager；Python 依赖调用方或上层装配。

### 4.2 `common`

#### 4.2.1 配置模型

| Python | Java | 方法/字段映射 | 结论 |
| --- | --- | --- | --- |
| `common/config.py::KnowledgeBaseConfig` | `KnowledgeBaseConfig` | 字段模型映射，Java 通过 getter/setter 暴露 | 对齐 |
| `common/config.py::RetrievalConfig` | `RetrievalConfig` | 字段映射: `top_k/use_graph/agentic/graph_expansion/filters/score_threshold` -> `topK/useGraph/agentic/graphExpansion/filters/scoreThreshold` | 对齐 |
| `common/config.py::IndexConfig` | `IndexConfig` | 字段模型映射 | 对齐 |
| `common/config.py::StoreType` | `StoreType` | `Milvus/Chroma/PGVector` -> `MILVUS/CHROMA/PGVECTOR` | 对齐 |
| `common/config.py::VectorStoreConfig` | `VectorStoreConfig` | 字段映射 + Java `validate()` | 对齐 |
| `common/config.py::RerankerConfig` | `RerankerConfig` | `api_key/api_base/model_name/timeout/temperature/top_p/yes_no_ids/extra_body` -> camelCase 字段 + getter/setter | 对齐 |

#### 4.2.2 文档与结果模型

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `common/document.py::Document` | `Document` | 字段模型映射 | 对齐 |
| `common/document.py::TextChunk` | `TextChunk` | `from_document -> fromDocument` | 对齐 |
| `common/document.py::MultimodalDocument` | `MultimodalDocument` | `content -> getContent`；`add_field -> addField` | 主 API 对齐 |
| `common/retrieval_result.py::SearchResult` | `SearchResult` | 字段模型映射 | 对齐 |
| `common/retrieval_result.py::RetrievalResult` | `RetrievalResult` | 字段模型映射 | 对齐 |
| `common/retrieval_result.py::MultiKBRetrievalResult` | `MultiKBRetrievalResult` | 字段模型映射 | 对齐 |
| `common/triple.py::Triple` | `Triple` | 字段模型映射 | 对齐 |
| `common/triple_beam.py::TripleBeam` | `TripleBeam` | `__getitem__ -> get`；`__len__ -> size`；`__contains__ -> contains`；`triples -> getTriples`；`score -> getScore`；`__iter__ -> iterator` | 对齐 |
| `common/triple_memory.py::TripleMemory` | `TripleMemory` | `__len__ -> size`；`triples_str -> getTriplesStr`；`extend_memory -> extendMemory`；`batch_extend_memory -> batchExtendMemory` | 对齐 |

#### 4.2.3 回调与结果排序

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `common/callbacks.py::BaseCallback` | `BaseCallback` | `__call__ -> onBatch`；`call_counter -> getCallCounter` | 对齐 |
| `common/callbacks.py::TqdmCallback` | `TqdmCallback` | `__call__ -> onBatch`；`__len__ -> length` | 语义对齐，Java 无 UI 进度条依赖 |
| `common/result_ranking.py::register_result_ranker_cls` | `ResultRankRegistry.registerResultRankerClass` | Python 全局注册表 -> Java 静态注册表 | 对齐 |
| `common/result_ranking.py::BaseRankConfig` | `BaseRankConfig` | `args -> getArgs`；`is_active -> isActive`；`get_ranker_cls -> getRankerClass` | 对齐 |
| `common/result_ranking.py::WeightedRankConfig` | `WeightedRankConfig` | `args -> getArgs` | 对齐 |
| `common/result_ranking.py::RRFRankConfig` | `RRFRankConfig` | `args -> getArgs`；`is_active -> isActive` | 对齐 |

Java 额外补充:

- `LoggingCallback`
- `RetrievalValidation`
- `RetrievalExceptions`

### 4.3 `embedding`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `embedding/base.py::Embedding` | `Embedding` | `embed_query -> embedQuery`；`embed_documents -> embedDocuments`；`dimension -> getDimension`；`max_batch_size -> getMaxBatchSize` | 对齐 |
| `embedding/api_embedding.py::APIEmbedding` | `APIEmbedding` | `embed_query_sync -> embedQuery`；`embed_documents_sync -> embedDocuments`；`dimension -> getDimension` | 主能力对齐 |
| `embedding/openai_embedding.py::OpenAIEmbedding` | `OpenAIEmbedding` | `embed_query/embed_documents` 对位 | 对齐 |
| `embedding/vllm_embedding.py::VLLMEmbedding` | `VLLMEmbedding` | `parse_multimodal_input -> parseMultimodalInput`；`embed_multimodal_sync -> embedMultimodalSync` | 对齐 |
| `embedding/utils.py::parse_base64_embedding` | `EmbeddingUtils.parseBase64Embedding` | 模块函数 -> Java 静态工具 | 对齐 |
| 无 | `HashEmbedding` | Java 本地哈希 embedding | Java 额外能力 |

### 4.4 `indexing.indexer`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `indexing/indexer/base.py::Indexer` | `Indexer` | `build_index -> buildIndex`；`update_index -> updateIndex`；`delete_index -> deleteIndex`；`index_exists -> indexExists`；`get_index_info -> getIndexInfo` | 对齐 |
| `chroma_indexer.py::ChromaIndexer` | `ChromaIndexer` | 类型对位 | 对齐 |
| `milvus_indexer.py::MilvusIndexer` | `MilvusIndexer` | 类型对位 | 对齐 |
| 无 | `InMemoryIndexer` | 本地内存索引器 | Java 额外能力 |
| Python 依赖直接实例化 | `IndexerFactory.createIndexer` | 工厂化装配 | Java 额外适配 |
| `base.py` 中属性契约 | `IndexBackendConfig` | `database_name/distance_metric/text_field/vector_field/sparse_vector_field/metadata_field/doc_id_field` getter 契约 | Java 额外抽象层 |

### 4.5 `indexing.processor`

#### 4.5.1 公共基类

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `processor/base.py::Processor` | `Processor<I, O>` | `process -> process` | 对齐 |
| `parser/base.py::Parser` | `Parser` | `parse -> parse`；`supports -> supports`；`process -> process` | 对齐 |
| `extractor/base.py::Extractor` | `Extractor` | `extract -> extract`；`process -> process` | 对齐 |
| `chunker/base.py::Chunker` | `Chunker` | `chunk_text -> chunkText`；`chunk_documents -> chunkDocuments`；`process -> process` | 对齐 |
| `splitter/base.py::Splitter` | `splitter/Splitter` | `split_text -> splitText`；`get_nodes_from_documents -> getNodesFromDocuments`；`process -> process` | 对齐 |

#### 4.5.2 chunker / splitter / parser / extractor 具体类型

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `chunker/char_chunker.py::CharChunker` | `CharChunker` | `chunk_text -> chunkText` | 对齐 |
| `chunker/tokenizer_chunker.py::TokenizerChunker` | `TokenizerChunker` | `chunk_text -> chunkText`；Java 额外 `getTokenizer/getLanguage/getSplitterConfig` | 对齐 |
| `chunker/chunking.py::TextChunker` | `TextChunker` | `get_chunker` 逻辑被内联；`chunk_documents -> chunkDocuments` | 主流程对齐 |
| `chunker/hybrid_chunker.py::HybridChunker` | `HybridChunker` | `chunk_text -> chunkText`；`chunk_documents -> chunkDocuments` | 对齐 |
| `chunker/text_preprocessor.py::{WhitespaceNormalizer,URLEmailRemover,SpecialCharacterNormalizer,PreprocessingPipeline}` | 同名 Java 类 | `process -> process`；`PreprocessingPipeline` 主 API 对齐 | 对齐 |
| `chunker/__init__.py::register_chunker/get_chunker` | `ChunkerRegistry.registerChunker/getChunker` | Python kwargs 工厂 -> Java `Supplier<Chunker>` 注册表 | 主能力存在，但扩展能力略弱 |
| `chunker/text_splitter.py::{TextSplitter,CharSplitter,IndexSentenceSplitter}` | 无直接对位 | Python 额外文本切分抽象 | Java 缺少这一组公开类 |
| `splitter/splitter.py::SentenceSplitter` | `SentenceSplitter` | `__call__/split_text` -> `splitText` | 主能力对齐 |
| `extractor/triple_extractor.py::TripleExtractor` | `LLMTripleExtractor` | `extract -> extract` | 对齐 |
| 无直接同名类 | `SimpleTripleExtractor` | Java 额外规则型 extractor | Java 额外能力 |
| `parser/auto_file_parser.py::register_parser` | `AutoFileParser.registerNewParser` | Python decorator 注册 -> Java 静态注册 | 对齐 |
| `parser/auto_file_parser.py::AutoFileParser` | `AutoFileParser` | `register_new_parser -> registerNewParser`；`get_supported_formats -> getSupportedFormats`；`supports -> supports`；`parse -> parse` | 对齐 |
| `parser/auto_link_parser.py::AutoLinkParser` | `AutoLinkParser` | `supports -> supports`；`parse -> parse` | 对齐 |
| `parser/auto_parser.py::AutoParser` | `AutoParser` | `supports -> supports`；`parse -> parse` | 对齐 |
| `parser/captioner.py::ImageCaptioner` | `ImageCaptioner` | `cp_image -> cpImage`；`captionImages` 对位 | 对齐 |
| `parser/{TxtMdParser,PDFParser,WordParser,ExcelParser,ImageParser,JSONParser,WebPageParser,WeChatArticleParser}` | `TxtMdParser/PDFParser/WordParser/ExcelParser/ImageParser/JsonParser/WebPageParser/WeChatArticleParser` | `parse -> parse`；`supports -> supports` | 对齐 |

补充说明:

- Java `AutoFileParser` 已注册 `.txt/.md/.json/.pdf/.docx/.xlsx/.csv/.tsv/.png/.jpg/.jpeg/.webp/.gif/.jfif`，覆盖 Python 主路径。
- Java `ChunkerRegistry` 已内置 `char/token/text/hybrid` 四种注册项，其中 `hybrid` 已存在，不再属于缺漏。

### 4.6 `retriever`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `retriever/base.py::Retriever` | `Retriever` | `retrieve -> retrieve`；`batch_retrieve -> batchRetrieve`；`retrieve_search_results -> retrieveSearchResults`；`close -> close` | 对齐 |
| `vector_retriever.py::VectorRetriever` | `VectorRetriever` | `retrieve -> retrieve`；`retrieve_search_results -> retrieveSearchResults` | 对齐 |
| `sparse_retriever.py::SparseRetriever` | `SparseRetriever` | `retrieve -> retrieve`；`retrieve_search_results -> retrieveSearchResults` | 对齐 |
| `hybrid_retriever.py::HybridRetriever` | `HybridRetriever` | `retrieve -> retrieve`；`retrieve_search_results -> retrieveSearchResults` | 对齐 |
| `graph_retriever.py::TripleBeamSearch` | `TripleBeamSearch` | `beam_search -> beamSearch` | 对齐 |
| `graph_retriever.py::GraphRetriever` | `GraphRetriever` | `get_retriever_for_mode -> getRetrieverForMode`；`retrieve -> retrieve`；`graph_expansion -> graphExpansion` | 对齐 |
| `agentic_retriever.py::AgenticRetriever` | `AgenticRetriever` | `is_graph_retriever -> isGraphRetriever`；`default_mode -> getDefaultMode`；`retrieve -> retrieve`；`batch_retrieve -> batchRetrieve` | 对齐 |
| 无 | `AbstractRetriever` | batch 默认实现 | Java 额外抽象层 |
| 无 | `AbstractStoreBackedRetriever` | store/embed/indexType 共享抽象 | Java 额外抽象层 |

### 4.7 `query_rewriter`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `query_rewriter.py::QueryRewriter` | `QueryRewriter` | `compress -> compress`；`rewrite(query) -> rewrite(String)`；`load_template -> loadTemplate`；`msg_2_text -> msgToText` | 对齐 |
| `query_rewriter.py` 模块辅助函数 `_fill_template/_extract_json/_parse_llm_json/_schema_repair/...` | `QueryRewriter.fillTemplate/extractJson/parseLlmJson/schemaRepair/...` | Python 模块函数 -> Java 静态/私有辅助 | 对齐 |
| 无 | `QueryRewriter.rewrite(String, List<RetrievalResult>)` | 利用已有检索结果做轻量改写 | Java 额外能力 |

### 4.8 `reranker`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `reranker/base.py::Reranker` | `Reranker` | Python `rerank/rerank_sync` -> Java `rerank` | 仅部分对齐 |
| `standard_reranker.py::StandardReranker` | `StandardReranker` | Python `rerank_sync` 的打分语义 -> Java `rerankScores`；候选重排 -> `rerank` | 主能力对齐，但接口形态不同 |
| `chat_reranker.py::ChatReranker` | `ChatReranker` | 类型对位 | 对齐 |
| 无直接同名类 | `LexicalReranker` | Java 额外本地 lexical reranker | Java 额外能力 |

补充说明:

- Python `Reranker` 抽象返回 `dict[str, float]`，且同时暴露 async/sync 接口；Java 抽象层只承诺 `List<RetrievalResult> rerank(...)`，打分字典能力被下沉到 `StandardReranker.rerankScores(...)` 具体类。

### 4.9 `vector_store`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `vector_store/base.py::VectorStore` | `VectorStore` | `add -> add`；`search -> search`；`sparse_search -> sparseSearch`；`hybrid_search -> hybridSearch`；`delete -> delete`；`table_exists -> tableExists`；`delete_table -> deleteTable` | 主能力对齐 |
| `vector_store/store.py::create_vector_store` | `VectorStoreFactory.createVectorStore` | Python 模块工厂 -> Java 静态工厂 | 对齐 |
| `chroma_store.py::ChromaVectorStore` | `ChromaVectorStore` | 类型对位 | 对齐 |
| `milvus_store.py::MilvusVectorStore` | `MilvusVectorStore` | 类型对位 | 对齐 |
| `pg_store.py::PGVectorStore` | `PGVectorStore` | 类型对位 | 对齐 |
| 无 | `InMemoryVectorStore` | Java 内存向量库 | Java 额外能力 |
| 无 | `SchemaMutableVectorStore` | schema/metadata 可变能力 | Java 额外能力 |

补充说明:

- Python `VectorStore` 抽象额外定义了 `create_client`、`check_vector_field`、`_check_configs_matching`；Java 没有统一接口级对位，更多通过 `VectorStoreFactory`、具体实现构造器和 `IndexBackendConfig` getter 契约承载。
- Java `VectorStore` 额外暴露 `withCollection`、`queryByFilters`、`count`，便于 graph retriever 和统计逻辑复用。

### 4.10 `utils`

| Python | Java | 方法映射 | 结论 |
| --- | --- | --- | --- |
| `utils/common.py::deduplicate` | `CommonUtils.deduplicate` | 模块函数 -> Java 静态工具 | 对齐 |
| `utils/fusion.py::rrf_fusion` | `FusionUtils.rrfFusionRetrieval/rrfFusionSearch` | Java 额外支持 `SearchResult` 与 weighted fusion | 对齐 |
| `utils/config_manager.py::ConfigManager` | `ConfigManager` | `load_from_file -> loadFromFile`；`save_to_file -> saveToFile`；`get_config -> getConfig`；`get_knowledge_base_config -> getKnowledgeBaseConfig`；`update_config -> updateConfig` | 对齐 |
| `utils/api_requests.py::{sync_request_with_retry,async_request_with_retry}` | `ApiRequestUtils.postJsonWithRetry` | Java 提供同步重试 helper | 仅部分对齐 |
| `lazy_load.py::lazy_load` | 无直接对位 | Python 动态导入入口 | Java 无需同构实现 |

## 5. Java 侧额外能力或适配，不计为缺漏

- `HashEmbedding`
- `InMemoryIndexer` / `InMemoryVectorStore`
- `AbstractRetriever` / `AbstractStoreBackedRetriever`
- `LexicalReranker`
- `LoggingCallback`
- `SchemaMutableVectorStore`
- `QueryRewriter.rewrite(String, List<RetrievalResult>)`
- `VectorStore.withCollection/queryByFilters/count`
- `FusionUtils.weightedFusionRetrieval/weightedFusionSearch`

## 6. 本轮确认的真实缺漏或部分差异

### 6.1 高优先级

| 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- |
| 顶层 `KnowledgeBase` | 公开 `delete_collection(collection)` | 无直接公开对位 API | 上层若按 Python 顶层接口管理 collection，Java 需下沉到 `VectorStore.deleteTable(...)` |
| `SimpleKnowledgeBase.retrieve_multi_kb` | 返回 `List[str]`，并支持 `config/top_k` | Java `retrieveMultiKb(...)` 返回 `List<RetrievalResult>`，且不接收 `RetrievalConfig` | 多 KB 聚合 helper 不能直接按 Python 契约替换 |
| `graph_knowledge_base.py` helper | `retrieve_multi_graph_kb` / `retrieve_multi_graph_kb_with_source` | 无同名公开 helper | graph 多库聚合辅助接口缺失 |
| `reranker/base.py::Reranker` | 抽象层同时支持 async/sync，返回打分字典 | Java 抽象层仅有 `rerank(List<RetrievalResult>)` | 若上层依赖“候选->score 映射”抽象契约，需要直接绑定具体实现而非接口 |

### 6.2 中优先级

| 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- |
| `vector_store/base.py::VectorStore` | 公开 `check_vector_field` / `create_client` 契约 | Java 无统一接口级对位 | 顶层知识库无法像 Python 一样对所有 store 做统一实际库字段校验 |
| `chunker/text_splitter.py` | `TextSplitter` / `CharSplitter` / `IndexSentenceSplitter` 为公开类 | Java 无同名公开类族 | Python 用户若直接依赖这组 splitter，需要单独适配 |
| `chunker/__init__.py` | `get_chunker(name, **kwargs)` 支持参数化工厂 | Java `ChunkerRegistry.getChunker(name)` 仅支持零参 `Supplier` | 注册表扩展能力弱于 Python |
| `utils/api_requests.py` | sync/async 重试 + 自定义状态码回调 | Java 仅有同步 `postJsonWithRetry` | 公共请求工具层的可扩展性较弱 |

### 6.3 低优先级

| 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- |
| `KnowledgeBase(strict_validation=...)` | 对外公开严格校验开关 | Java 无等价公开参数 | 无法精确复现 Python 的“严格/宽松”模式 |
| `AutoFileParser.parse()` 元数据增强 | Python 会补 `doc_id/title/file_path/file_ext` | Java 当前只稳定补 `file_ext` | 解析后 metadata 信息少于 Python |

## 7. 结论

- retrieval 模块已经不是“Java 侧缺失模块”，而是“主干已齐、少量辅助接口和公共抽象契约仍未完全追平 Python”。
- 对业务主链最重要的知识库增删改查、向量/稀疏/混合/图检索、chunking、parser、embedding、query rewriter、reranker 已经基本具备对位实现。
- 需要继续追平时，建议优先顺序为: `multi-kb helper 契约 -> KnowledgeBase / VectorStore 顶层缺口 -> Reranker 抽象层 -> TextSplitter 家族与注册表扩展能力`。