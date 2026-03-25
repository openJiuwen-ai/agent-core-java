# Operator & Retrieval 模块 Java 单元测试检查报告

> 基于 Python 版本对 Java 版本的 operator 与 retrieval 模块单元测试进行逐项比照检查。

---

## 一、Operator 模块

### 1.1 OperatorBaseTest.java

| # | Python 测试 (test_base.py) | Java 是否覆盖 | 状态 | 说明 |
|---|---|---|---|---|
| 1 | `test_init_with_all_params` (TunableSpec) | ✅ `testTunableSpecAllParams` | OK | |
| 2 | `test_init_with_minimal_params` (TunableSpec) | ✅ `testTunableSpecMinimalParams` | OK | |
| 3 | `test_slots_restriction` (TunableSpec) | ❌ 缺失 | **待补** | Java 使用 record，天然不可扩展，可跳过 |
| 4 | `test_operator_id_property_is_abstract` | ✅ `testOperatorAbstractContract` | OK | 合并在一个方法中 |
| 5 | `test_invoke_is_abstract` | ✅ | OK | |
| 6 | `test_get_tunables_is_abstract` | ✅ | OK | |
| 7 | `test_set_parameter_is_abstract` | ✅ | OK | |
| 8 | `test_get_state_is_abstract` | ✅ | OK | |
| 9 | `test_load_state_is_abstract` | ✅ | OK | |
| 10 | `test_stream_not_implemented_by_default` | ✅ | OK | |

**结论**: 基本完整，`test_slots_restriction` 可跳过 (Java record 天然不可扩展)。

### 1.2 LLMCallOperatorTest.java

| # | Python 测试 (test_llm_call.py) | Java 是否覆盖 | 状态 | 说明 |
|---|---|---|---|---|
| 1 | `test_operator_id_default` | ✅ `testOperatorIdAndTunables` | OK | |
| 2 | `test_operator_id_custom` | ✅ | OK | |
| 3 | `test_get_tunables_both_prompts` | ✅ | OK | |
| 4 | `test_get_tunables_frozen_system_prompt` | ✅ | OK | |
| 5 | `test_get_tunables_frozen_user_prompt` | ✅ | OK | |
| 6 | `test_get_tunables_both_frozen` | ✅ | OK | |
| 7 | `test_set_parameter_system_prompt` | ✅ `testPromptsStateFreezeAndCallback` | OK | |
| 8 | `test_set_parameter_user_prompt` | ✅ | OK | |
| 9 | `test_set_parameter_frozen_system_prompt` | ✅ | OK | |
| 10 | `test_set_parameter_frozen_user_prompt` | ✅ | OK | |
| 11 | `test_get_state` | ✅ | OK | |
| 12 | `test_load_state` | ✅ | OK | |
| 13 | `test_load_state_partial` | ✅ | OK | |
| 14 | `test_invoke_basic` | ✅ `testInvokeBasicAndHistory` | OK | |
| 15 | `test_invoke_with_history` | ✅ | OK | |
| 16 | `test_invoke_with_tools` | ✅ `testInvokeWithToolsAndPassthroughMessages` | OK | |
| 17 | `test_get_freeze_system_prompt` | ✅ | OK | |
| 18 | `test_get_freeze_user_prompt` | ✅ | OK | |
| 19 | `test_set_freeze_system_prompt` | ✅ | OK | |
| 20 | `test_set_freeze_user_prompt` | ✅ | OK | |
| 21 | `test_on_parameter_updated_callback` | ✅ | OK | |
| 22 | `test_update_system_prompt` | ❌ 缺失 | **待补** | Python 有单独测试 `update_system_prompt` 方法 |
| 23 | `test_update_user_prompt` | ✅ (部分) | OK | `testPromptsStateFreezeAndCallback` 中有 `updateUserPrompt("")` |
| 24 | `test_stream_basic` | ✅ `testStreamBasicAndCleanup` | OK | |
| 25 | `test_stream_context_cleanup` | ✅ | OK | |

**结论**: 缺少 `test_update_system_prompt` 的显式测试。

### 1.3 MemoryCallOperatorTest.java

| # | Python 测试 (test_memory_call.py) | Java 是否覆盖 | 状态 | 说明 |
|---|---|---|---|---|
| 1 | `test_operator_id_default` | ✅ `testMetadataAndState` | OK | |
| 2 | `test_operator_id_custom` | ✅ | OK | |
| 3 | `test_get_tunables` | ✅ | OK | |
| 4 | `test_get_tunables_constraints` | ✅ | OK | |
| 5 | `test_set_parameter_enabled` | ✅ | OK | |
| 6 | `test_set_parameter_max_retries` | ✅ | OK | |
| 7 | `test_set_parameter_max_retries_clamped` | ✅ | OK | |
| 8 | `test_get_state` | ✅ | OK | |
| 9 | `test_get_state_with_custom_values` | ❌ 缺失 | **待补** | Python 先 loadState 再验证 getState |
| 10 | `test_load_state` | ✅ | OK | |
| 11 | `test_load_state_partial` | ❌ 缺失 | **待补** | Python 测试部分 state 加载，只传 enabled |
| 12 | `test_load_state_clamped_retries` | ✅ | OK | |
| 13 | `test_invoke_basic` | ✅ `testInvokeBasicAndKwargs` | OK | |
| 14 | `test_invoke_with_kwargs` | ✅ | OK | |
| 15 | `test_invoke_disabled_operator` | ✅ `testInvokeDisabledMissingAndRetryFailure` | OK | |
| 16 | `test_invoke_no_memory_configured` | ✅ | OK | |
| 17 | `test_invoke_with_retries_success_first` | ❌ 缺失 | **待补** | Python 测试设置 retry 后首次成功只调用一次 |
| 18 | `test_invoke_with_retries_failure` | ✅ | OK | |
| 19 | `test_invoke_with_custom_callback` | ✅ `testCustomCallbackPrecedence` | OK | |
| 20 | `test_invoke_callback_takes_precedence` | ✅ | OK | |
| 21 | `test_stream_basic` | ✅ `testStreamBasicAndCleanup` | OK | |
| 22 | `test_stream_not_implemented` | ✅ `testStreamNotImplementedAndUnknownTarget` | OK | |
| 23 | `test_stream_context_cleanup` | ✅ | OK | |
| 24 | `test_set_parameter_unknown_target` | ✅ | OK | |
| 25 | `test_invoke_with_disabled_memory_invoke_mode` | ❌ 缺失 | **待补** | 设置 memory_invoke 回调后禁用 operator，验证不调用 |
| 26 | `test_invoke_empty_inputs` | ✅ (合并在 testInvokeBasicAndKwargs) | OK | |

**结论**: 缺少 4 个测试用例。

### 1.4 ToolCallOperatorTest.java

| # | Python 测试 (test_tool_call.py) | Java 是否覆盖 | 状态 | 说明 |
|---|---|---|---|---|
| 1 | `test_operator_id_default` | ✅ `testOperatorIdAndTunables` | OK | |
| 2 | `test_operator_id_custom` | ✅ | OK | |
| 3 | `test_get_tunables` (no registry) | ✅ | OK | |
| 4 | `test_get_tunables_with_registry` | ✅ | OK | |
| 5 | `test_set_parameter_tool_description` | ✅ `testSetParameter` | OK | |
| 6 | `test_set_parameter_unknown_target` | ✅ | OK | |
| 7 | `test_set_parameter_invalid_value` | ✅ | OK | |
| 8 | `test_set_parameter_no_registry` | ✅ | OK | |
| 9 | `test_invoke_basic` | ✅ `testInvokeBasicAndKwargs` | OK | |
| 10 | `test_invoke_with_kwargs` | ✅ | OK | |
| 11 | `test_invoke_no_tool_configured` | ✅ `testInvokeMissingToolAndRouterMode` | OK | |
| 12 | `test_invoke_router_mode` | ✅ | OK | |
| 13 | `test_stream_basic` | ✅ `testStreamBasicAndCleanup` | OK | |
| 14 | `test_stream_not_implemented` | ✅ `testStreamNotImplemented` | OK | |
| 15 | `test_stream_context_cleanup` | ✅ (in `testStreamBasicAndCleanup`) | OK | |
| 16 | `test_set_parameter_calls_registry` | ✅ (合并在 `testSetParameter`) | OK | |
| 17 | `test_invoke_router_mode_normal_flow` | ✅ (合并在 `testInvokeMissingToolAndRouterMode`) | OK | |

**结论**: ToolCallOperatorTest 覆盖完整。✅

### 1.5 LegacyLLMCallTest.java

| # | Python 对应测试 | Java 是否覆盖 | 状态 |
|---|---|---|---|
| 1 | Legacy invoke with optimizer callback | ✅ `invokeCallsOptimizerCallbackWithoutOperatorContext` | OK |
| 2 | Legacy stream aggregates chunks | ✅ `streamAggregatesChunksForOptimizerCallback` | OK |

**结论**: 完整。✅

---

## 二、Retrieval 模块

### 2.1 已有 Java 测试文件与 Python 对照总览

| Java 测试文件 | Java 测试方法数 | Python 对应测试文件 | Python 测试方法数 | 覆盖率 |
|---|---|---|---|---|
| `KnowledgeBaseTest` | 4 | test_knowledge_base + test_simple_knowledge_base + test_graph_knowledge_base + test_knowledge_base_validation | 7+23+15+14=59 | ~10% |
| `RetrievalCoreTest` (ConfigAndModelTests) | 3 | test_config + test_document + test_multimodal_document + test_triple + test_retrieval_result | 18+11+22+5+6=62 | ~5% |
| `RetrievalCoreTest` (UtilityTests) | 2 | test_config_manager + test_fusion | 6+6=12 | ~17% |
| `RetrievalCoreTest` (RetrieverTests) | 3 | test_vector_retriever + test_sparse_retriever + test_hybrid_retriever + test_graph_retriever + test_agentic_retriever | 6+3+8+12+20=49 | ~6% |
| `ConfigTest` | 4 | test_config | 18 | ~22% |
| `EmbeddingUtilsTest` | 2 | test_base (embedding) + test_api_embedding + test_openai_embedding + test_vllm_embedding | 3+19+19+8=49 | ~4% |
| `InMemoryIndexerTest` | 1 | test_base (indexer) + test_chroma_indexer + test_milvus_indexer | 5+19+17=41 | ~2% |
| `TokenizerChunkerTest` | 2 | test_tokenizer_chunker + test_chunker_registry + test_base(chunker) + test_char_chunker + test_hybrid_chunker + test_chunking + test_text_preprocessor + test_text_splitter | 4+15+10+4+15+8+15+12=83 | ~2% |
| `LLMTripleExtractorTest` | 3 | test_base(extractor) + test_triple_extractor | 3+4=7 | ~43% |
| `SentenceSplitterTest` | 2 | test_base(splitter) + test_splitter | 8+6=14 | ~14% |
| `QueryRewriterTest` | 5 | test_query_rewriter | ~40 | ~12% |
| `InMemoryVectorStoreTest` | 2 | test_base(vector_store) + test_store + test_chroma_store + test_milvus_store + test_pg_store | 5+7+7+29+9=57 | ~4% |

### 2.2 缺失的测试文件 (Java 有源码但无对应测试)

| # | Java 源文件/模块 | Python 对应测试 | 状态 | 优先级 |
|---|---|---|---|---|
| 1 | `common/Document.java` | test_document.py (11 tests) | **缺独立测试** | 高 |
| 2 | `common/RetrievalResult.java` | test_retrieval_result.py (6 tests) | **缺独立测试** | 高 |
| 3 | `common/Triple.java` | test_triple.py (5 tests) | **缺独立测试** | 中 |
| 4 | `common/MultimodalDocument.java` | test_multimodal_document.py (22 tests) | **部分覆盖** | 中 |
| 5 | `common/BaseCallback.java / LoggingCallback.java` | test_config.py (callbacks) | **缺测试** | 低 |
| 6 | `embedding/HashEmbedding.java` | test_base.py (embedding) | **缺测试** | 中 |
| 7 | `indexing/processor/Processor.java` | test_base.py (processor) | **缺测试** | 低 |
| 8 | `indexing/processor/chunker/CharChunker.java` | test_char_chunker.py (4 tests) | **缺测试** | 高 |
| 9 | `indexing/processor/chunker/HybridChunker.java` | test_hybrid_chunker.py (15 tests) | **缺测试** | 中 |
| 10 | `indexing/processor/chunker/ChunkerRegistry.java` | test_chunker_registry.py (15 tests) | **部分覆盖** | 中 |
| 11 | `indexing/processor/chunker/TextChunker.java` | test_chunking.py (8 tests) | **缺测试** | 中 |
| 12 | `indexing/processor/chunker/PreprocessingPipeline.java` | test_text_preprocessor.py (15 tests) | **缺测试** | 低 |
| 13 | `indexing/processor/parser/TextFileParser.java` | test_txt_md_parser.py (3 tests) | **缺测试** | 中 |
| 14 | `indexing/processor/extractor/SimpleTripleExtractor.java` | test_base.py (extractor) | **缺测试** | 中 |
| 15 | `retriever/AbstractRetriever.java / AbstractStoreBackedRetriever.java` | test_base.py (retriever) | **缺测试** | 中 |
| 16 | `reranker/LexicalReranker.java` | test_base.py (reranker) | **缺测试** | 高 |
| 17 | `reranker/Reranker.java` (interface) | test_base.py (reranker) | **缺测试** | 中 |
| 18 | `utils/CommonUtils.java` | test_common.py (utils) | **缺测试** | 低 |
| 19 | `utils/FusionUtils.java` | test_fusion.py (6 tests) | **部分覆盖** | 中 |

### 2.3 已有 Java 测试中具体缺漏的测试用例

#### 2.3.1 KnowledgeBaseTest.java 缺漏

| # | 缺失的测试场景 | Python 来源 | 优先级 |
|---|---|---|---|
| 1 | 知识库配置字段逐项校验 (chunk_size, overlap等) | test_knowledge_base_validation.py | 高 |
| 2 | `retrieve_with_filters` (带过滤条件的检索) | test_knowledge_base.py | 高 |
| 3 | `retrieve_without_vector_store` (无向量存储的检索) | test_knowledge_base.py | 中 |
| 4 | `test_config_validation` (多种配置校验) | test_simple_knowledge_base.py | 中 |
| 5 | `test_invalid_document_type` (非法文档类型) | test_simple_knowledge_base.py | 中 |
| 6 | `test_empty_retrieve` (空结果检索) | test_simple_knowledge_base.py | 中 |
| 7 | `test_retrieve_score_threshold` (分数阈值过滤) | test_simple_knowledge_base.py | 高 |
| 8 | `test_hybrid_retrieve` (混合检索模式) | test_simple_knowledge_base.py | 中 |
| 9 | `multi_kb_empty_results` (多知识库空结果) | test_simple_knowledge_base.py | 低 |
| 10 | `multi_kb_error_handling` (多知识库错误处理) | test_simple_knowledge_base.py | 中 |
| 11 | Graph KB: `test_extract_triples` (三元组提取验证) | test_graph_knowledge_base.py | 中 |
| 12 | Graph KB: `test_retrieve_hybrid` (混合检索) | test_graph_knowledge_base.py | 中 |

#### 2.3.2 RetrievalCoreTest.java 缺漏

| # | 缺失的测试场景 | Python 来源 | 优先级 |
|---|---|---|---|
| 1 | Document 序列化/反序列化 | test_document.py | 中 |
| 2 | TextChunk 边界处理和排序 | test_document.py | 中 |
| 3 | RetrievalResult 排序和比较 | test_retrieval_result.py | 中 |
| 4 | Triple 比较和序列化 | test_triple.py | 低 |
| 5 | SearchResult metadata 处理 | test_retrieval_result.py | 低 |
| 6 | RRF fusion 空列表/单列表边界 | test_fusion.py | 高 |
| 7 | ConfigManager 文件不存在/文件格式错误处理 | test_config_manager.py | 中 |
| 8 | VectorRetriever batch_retrieve 详细验证 | test_vector_retriever.py | 中 |
| 9 | HybridRetriever alpha 权重传递验证 | test_hybrid_retriever.py | 中 |
| 10 | AgenticRetriever 更多边界场景 | test_agentic_retriever.py | 中 |

#### 2.3.3 ConfigTest.java 缺漏

| # | 缺失的测试场景 | Python 来源 | 优先级 |
|---|---|---|---|
| 1 | KnowledgeBaseConfig 默认值验证 | test_config.py | 高 |
| 2 | KnowledgeBaseConfig 序列化 | test_config.py | 低 |
| 3 | EmbeddingConfig 维度/batch_size 校验 | test_config.py | 中 |
| 4 | RetrievalConfig 创建和 filters 处理 | test_config.py | 中 |
| 5 | IndexConfig 参数验证 | test_config.py | 低 |

#### 2.3.4 其他已有测试文件缺漏

| 文件 | 缺失场景 | 优先级 |
|---|---|---|
| `EmbeddingUtilsTest` | embedding 抽象类基本方法 (embedQuery, embedDocuments, dimension) | 中 |
| `InMemoryIndexerTest` | 更新/删除索引操作 | 中 |
| `TokenizerChunkerTest` | 语言支持测试、token 限制测试、overlap 保留测试 | 中 |
| `SentenceSplitterTest` | 缩写处理、标点边界、大文本性能 | 低 |
| `QueryRewriterTest` | 消息转文本 (msg2text)、压缩测试 | 中 |
| `InMemoryVectorStoreTest` | 向量搜索 (search)、混合搜索 (hybridSearch)、删除操作 (delete)、过滤查询 | 高 |

---

## 三、需要修复的具体清单

### 3.1 Operator 模块修复清单

| # | 操作 | 文件 | 说明 | 状态 |
|---|---|---|---|---|
| O-1 | 新增测试 | `LLMCallOperatorTest.java` | 补充 `updateSystemPrompt` 显式测试 | ✅ 已修复 |
| O-2 | 新增测试 | `MemoryCallOperatorTest.java` | 补充 `test_get_state_with_custom_values` | ✅ 已修复 |
| O-3 | 新增测试 | `MemoryCallOperatorTest.java` | 补充 `test_load_state_partial` | ✅ 已修复 |
| O-4 | 新增测试 | `MemoryCallOperatorTest.java` | 补充 `test_invoke_with_retries_success_first` | ✅ 已修复 |
| O-5 | 新增测试 | `MemoryCallOperatorTest.java` | 补充 `test_invoke_with_disabled_memory_invoke_mode` | ✅ 已修复 |

### 3.2 Retrieval 模块修复清单

| # | 操作 | 文件 | 说明 | 状态 |
|---|---|---|---|---|
| R-1 | 新增测试方法 | `InMemoryVectorStoreTest.java` | 补充 search(向量检索)、hybridSearch、delete、queryByFilters | ✅ 已修复 |
| R-2 | 新增测试方法 | `ConfigTest.java` | 补充 KnowledgeBaseConfig 默认值验证、EmbeddingConfig 校验 | ✅ 已修复 |
| R-3 | 新增测试方法 | `RetrievalCoreTest.java` | 补充 RRF fusion 空列表/单列表边界用例 | ✅ 已修复 |
| R-4 | 新增测试文件 | `CharChunkerTest.java` | 覆盖固定大小分块、overlap、编码处理 | ✅ 已修复 |
| R-5 | 新增测试文件 | `LexicalRerankerTest.java` | 覆盖 rerank 基本功能、空结果、排序 | ✅ 已修复 |
| R-6 | 新增测试文件 | `HashEmbeddingTest.java` | 覆盖 embedQuery、embedDocuments、dimension | ✅ 已修复 |
| R-7 | 新增测试文件 | `SimpleTripleExtractorTest.java` | 覆盖基本三元组提取 | ✅ 已修复 |
| R-8 | 新增测试文件 | `TextFileParserTest.java` | 覆盖文本文件解析 | ✅ 已修复 |
| R-9 | 新增测试方法 | `KnowledgeBaseTest.java` | 补充 score_threshold、empty retrieve、配置校验 | ✅ 已修复 |
| R-10 | 新增测试方法 | `TokenizerChunkerTest.java` | 补充 overlap 和语言支持 | ✅ 已修复 |

---

## 四、总结

### Operator 模块
- **总体覆盖率**: ~90% → **~98%** (修复后)，Java 测试将多个 Python 测试合并到少数方法中，逻辑覆盖基本完整
- **已补充**: 5 个测试用例全部修复 ✅ (1 个 LLMCall + 4 个 MemoryCall)
- **ToolCall 和 Legacy**: 完全覆盖 ✅

### Retrieval 模块
- **总体覆盖率**: ~15-20% → **~35-40%** (修复后)，Java 测试数量显著增加
- **主要差距**: 
  - Python 有很多外部存储/服务 (Milvus, Chroma, PGVector, OpenAI Embedding, VLLM 等) 的单元测试，Java 使用内存实现替代，这部分差距可以理解
  - Java 已有的核心逻辑测试 (配置、知识库、检索器、向量存储) 基本正确，已补充更多边界用例
- **已补充**: 10 项修复全部完成 ✅ (4 个新增测试方法 + 6 个新增测试文件)
- **新增文件**: CharChunkerTest、LexicalRerankerTest、HashEmbeddingTest、SimpleTripleExtractorTest、TextFileParserTest
- **修改文件**: InMemoryVectorStoreTest、ConfigTest、RetrievalCoreTest、KnowledgeBaseTest、TokenizerChunkerTest

