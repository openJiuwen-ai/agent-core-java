# foundation 模块 Python / Java API 映射

## 对照范围

- Python 基线：`F:\oepnjiuwen\agent-core-python\openjiuwen\core\foundation`
- Java foundation：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\foundation`
- Java SPI 补充：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\spi`

## 说明

- 本文以“公开 API、基础抽象、包级导出、工厂/注册入口”为主。
- Python 的 `snake_case` 一般映射到 Java 的 `camelCase`。
- Python `async`/`AsyncIterator` 一般映射到 Java 的同步调用 / `Iterator`。
- Python `Pydantic/dataclass` 字段一般映射到 Java 的 `getter`、`builder`、`record`。
- Java 中一部分 `store` 基础接口下沉到了 `com.openjiuwen.spi.*`，本文一并纳入 `foundation` 对照。
- Python 私有辅助方法（以下划线开头）只在它直接影响外部扩展点时补充说明，不逐个展开。

## 总体结论

- `llm`、`prompt` 主干 API 已经形成稳定映射，Java 还额外引入了工厂/SPI 注册层。
- `store` 的接口面已较第一轮明显补齐：`base_embedding`、`graph` 契约、`vector/utils`、`vector_fields`、`FieldSchema.defaultValue`、query dialect 定义都已存在。
- `tool` 主链路可用，但 `OpenApiClient`、schema extractor、局部 `kwargs` 兼容能力仍比 Python 简化。
- 第二轮复核发现：当前真正需要继续补的，不再是“有没有接口”，而是少数模块“接口已在、行为仍简化/未接通”。详见 `docs/FIXED/foundation_fixed.md`。

## 1. 包级入口与导出

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `foundation.llm.__all__` | `com.openjiuwen.core.foundation.llm.*` | `Model`、`BaseModelClient`、`BaseOutputParser`、schema/message/parser 类直接映射 | 适配映射 | Java 不保留 `__all__` 门面，改为显式类导入 |
| `foundation.prompt.__all__` | `prompt.PromptTemplate` + `prompt.assemble.*` | `PromptTemplate` 对位；`PromptAssembler`、`Variable` 家族在 Java 中显式暴露 | 适配映射 | Python 顶层只导出 `PromptTemplate`，Java 暴露得更直接 |
| `foundation.store.create_vector_store` + `store.__getattr__` | `StoreFactory.createVectorStore` + `spi.store.*` | `create_vector_store -> createVectorStore`；懒加载 `BaseDbStore/DbBasedKVStore/DefaultDbStore` 改为显式 SPI/实现类 | 适配映射 | Java 用 `Factory + SPI` 取代 Python 的 `PEP 562` 懒加载 |
| `foundation.tool.__all__` | `tool.*` + `tool.annotation.ToolDefinition` + `tool.function.AnnotatedToolFactory` | `Tool/LocalFunction/RestfulApi/MCPTool/McpClient` 等主类一一对位 | 适配映射 | Python 顶层 `tool()` 装饰器在 Java 中拆成注解与工厂扫描 |
| `store.query.__init__` 自动注册 dialect | `ChromaQueryDialect` / `MilvusQueryDialect` + `QueryDialectRegistration.ensureRegistered()` | Python `import` 即注册；Java 改为显式 `ensureRegistered()` | 部分映射 | Java 有定义和注册辅助类，但不是包导入即生效 |

## 2. llm

### 2.1 核心入口与 ModelClient

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `llm.Model` | `llm.Model` | `invoke/stream/generate_image/generate_speech/generate_video -> invoke/stream/generateImage/generateSpeech/generateVideo` | 适配映射 | Java 额外提供 `registerFactory()`，把 Python `_CLIENT_TYPE_REGISTRY` 升级为 `ModelClientFactory + DefaultModelClientFactories` |
| `llm.InferenceAffinityModel` | `llm.InferenceAffinityModel` | `invoke/stream/release -> invoke/stream/release` | 适配映射 | Java 额外暴露 `getModelConfig()/getModelClientConfig()` |
| `llm.model_clients.BaseModelClient` | `llm.model_clients.BaseModelClient` | `invoke/stream/generate_*` 一一对应；扩展点 `_get_client_name/_validate_config/_convert_messages_to_dict/_convert_tools_to_dict/_build_request_params -> getClientName/validateConfig/convertMessagesToDict/convertToolsToDict/buildRequestParams` | 适配映射 | Java 保留了与 Python 同职责的 protected 扩展点 |
| `llm.model_clients.OpenAIModelClient` | `llm.model_clients.OpenAiCompatibleModelClient` + `OpenAiModelClientFactory` | `invoke/stream -> invoke/stream` | 部分映射 | Java 合并为“OpenAI-compatible”通用实现，Python 专有内部辅助方法折叠到基类/实现内 |
| `llm.model_clients.DashScopeModelClient` | `llm.model_clients.DashScopeModelClient` + `DashScopeModelClientFactory` | `generate_image/generate_speech/generate_video -> generateImage/generateSpeech/generateVideo`；chat `invoke/stream` 由父类 `OpenAiCompatibleModelClient` 继承 | 完全映射 | Java 已有独立 DashScope 多模态 client，不再是第一轮文档里所说的缺失 |
| `llm.model_clients.InferenceAffinityModelClient` | `llm.model_clients.InferenceAffinityModelClient` + `InferenceAffinityModelClientFactory` | `invoke/stream/release -> invoke/stream/release` | 适配映射 | Java 显式提供 `UnsupportedOperationException` 版 `generateImage/generateSpeech/generateVideo`，与 Python“声明但不支持”语义一致 |
| `llm.model_clients.SiliconFlowModelClient` | `llm.model_clients.OpenAiCompatibleModelClient` + `SiliconFlowModelClientFactory` | `invoke/stream -> invoke/stream` | 部分映射 | Java 没有独立 `SiliconFlowModelClient` 类名，复用 OpenAI-compatible 实现；Python 中未实现的多模态 `generate_*` 也未额外补出 |
| Python `_CLIENT_TYPE_REGISTRY` | Java `Model.ModelClientFactory` + `DefaultModelClientFactories` | `OpenAI/OpenRouter/SiliconFlow/DashScope/InferenceAffinity` provider 注册 | Java 扩展 | Java 支持 `ServiceLoader` + 手工注册双通道 |

### 2.2 output_parsers

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `BaseOutputParser` | `BaseOutputParser` | `parse/stream_parse -> parse/streamParse` | 适配映射 | Java 用 `Iterator` 承载增量解析 |
| `JsonOutputParser` | `JsonOutputParser` | `parse/stream_parse -> parse/streamParse` | 完全映射 | Java 内部用迭代器实现流式拆分 |
| `MarkdownOutputParser` | `MarkdownOutputParser` | `parse/stream_parse -> parse/streamParse` | 完全映射 | 细粒度提取 helper 在 Java 内部实现，不单独暴露 |
| `MarkdownElementType` | `MarkdownElementType` | 枚举值一一对应 | 完全映射 | - |
| `MarkdownElement` | `MarkdownElement` | 数据字段一一对应 | 完全映射 | - |
| `MarkdownContent` | `MarkdownContent` | 数据字段一一对应 | 完全映射 | - |

### 2.3 schema、message、chunk

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ProviderType` | `ProviderType` | 枚举值一一对应，`.value -> getValue()` | 适配映射 | Java 额外提供 `fromValue()` |
| `ModelClientConfig` | `ModelClientConfig` | 字段 1:1；extra fields -> `extraFields` / `extraField()` | 部分映射 | Java 已有 builder 和 `@JsonAnySetter`，但 Python `validate_client_provider()` 的大小写归一化与注册校验未内置 |
| `ModelRequestConfig` | `ModelRequestConfig` | 字段 1:1；extra fields -> `extraFields/setExtraField()` | 完全映射 | - |
| `BaseModelInfo` | `BaseModelInfo` | 字段 1:1；extra fields -> `getExtraFields/setExtraField` | 适配映射 | Python `handle_model_name()` 的 alias 逻辑在 Java 里通过 `@JsonProperty("model")` 承载 |
| `ModelConfig` | `ModelConfig` | dataclass -> `record ModelConfig(String modelProvider, BaseModelInfo modelInfo)` | 完全映射 | - |
| `GenerationResponse` | `GenerationResponse` | 数据字段映射 | 完全映射 | - |
| `ImageGenerationResponse` | `ImageGenerationResponse` | 数据字段映射 | 完全映射 | - |
| `AudioGenerationResponse` | `AudioGenerationResponse` | 数据字段映射 | 完全映射 | - |
| `VideoGenerationResponse` | `VideoGenerationResponse` | 数据字段映射 | 完全映射 | - |
| `ToolCall` | `ToolCall` | 数据字段映射 | 完全映射 | - |
| `UsageMetadata` | `UsageMetadata` | 数据字段映射 | 完全映射 | - |
| `BaseMessage` | `BaseMessage` | `role/content/name` 一一对应 | 适配映射 | Java 额外提供 `getContentAsString()/getContentAsList()` |
| `AssistantMessage` | `AssistantMessage` | `convert_openai_tool_calls_format -> convertOpenAiToolCalls`；`model_dump -> toApiFormat` | 适配映射 | Python validator/serializer 逻辑在 Java 中拆成显式静态/实例方法 |
| `UserMessage` / `SystemMessage` / `ToolMessage` | `UserMessage` / `SystemMessage` / `ToolMessage` | role 默认值与字段保持一致 | 完全映射 | - |
| `BaseMessageChunk` | `BaseMessageChunk` | `__add__ -> merge` | 适配映射 | Java 把运算符重载显式化为 `merge()` |
| `AssistantMessageChunk` | `AssistantMessageChunk` | `__add__ -> merge` | 适配映射 | tool call fragment merge 逻辑已保留 |
| `ToolMessageChunk` | `ToolMessageChunk` | `__add__ -> merge` | 适配映射 | - |
| `merge_parser_content` | `MergeUtils.mergeParserContent` | 同职责映射 | 完全映射 | Java 还能识别 `Mergeable` 对象 |
| `merge_dicts` | `MergeUtils.mergeMaps` | 同职责映射 | 完全映射 | - |
| `merge_pydantic_models` | `MergeUtils.mergeObjects` | Pydantic model merge -> POJO field-level merge | 适配映射 | 第二轮确认：Java 已补上对象级 merge helper，不再缺失 |

## 3. prompt

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `PromptTemplate` | `PromptTemplate` | `to_messages/format -> toMessages/format` | 完全映射 | - |
| `PromptAssembler` | `PromptAssembler` | `input_keys/prompt_assemble -> getInputKeys/promptAssemble` | 适配映射 | Java 内部把 Python 的辅助步骤拆成私有实现 |
| `Variable` | `Variable` | `update/eval/prepare_inputs -> update/eval/prepareInputs` | 适配映射 | Java 额外暴露 `getName/setName/getInputKeys/getValue` |
| `TextableVariable` | `TextableVariable` | `update -> update` | 完全映射 | - |
| `DictableVariable` | `DictableVariable` | `update -> update` | 完全映射 | - |

## 4. store

### 4.1 db / embedding / kv / object

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `BaseDbStore` | `spi.store.BaseDbStore<E>` | `get_async_engine -> getEngine` | 适配映射 | Python `AsyncEngine` 改为 Java 泛型 `engine/data source` |
| `DefaultDbStore` | `store.db.DefaultDbStore` | `get_async_engine -> getEngine` | 适配映射 | Java 额外实现了 `DataSource` 接口相关方法 |
| `EmbeddingConfig` | `store.base_embedding.EmbeddingConfig` | 字段 1:1 | 完全映射 | 第二轮确认：Java 已补齐 |
| `Embedding` | `store.base_embedding.Embedding` | `embed_query/embed_documents/dimension -> embedQuery/embedDocuments/getDimension` | 适配映射 | 第二轮确认：Java 已补齐 |
| `BaseKVStore` | `spi.store.BaseKVStore` | `set/exclusive_set/get/exists/delete/get_by_prefix/delete_by_prefix/mget/batch_delete/pipeline -> set/exclusiveSet/get/exists/delete/getByPrefix/deleteByPrefix/mget/batchDelete/pipeline` | 完全映射 | - |
| `BasedKVStorePipeline` | `spi.store.KVStorePipeline` | `set/get/exists/execute -> set/get/exists/execute` | 完全映射 | Java 统一命名为 `KVStorePipeline` |
| `DbBasedKVStore` | `store.kv.DbBasedKVStore` | 主干 KV API 一一对应 | 完全映射 | - |
| `InMemoryKVStore` | `store.kv.InMemoryKVStore` | 主干 KV API 一一对应 | 完全映射 | - |
| `ShelveStore` | 无直接对位 | 无同职责实现 | 缺失 | Java 侧没有与 Python `shelve` 持久化 KV 对应的轻量 provider |
| `BaseObjectStorageClient` | `spi.store.object.BaseObjectStorageClient` | `upload_file/download_file/delete_object/create_bucket/delete_bucket/list_objects -> uploadFile/downloadFile/deleteObject/createBucket/deleteBucket/listObjects` | 完全映射 | - |
| `AioBotoClient` | `LocalObjectStorageClient` | `create_bucket/delete_bucket/upload_file/download_file/delete_object/list_objects` 对位 | 部分映射 | Java 具体 provider 改为本地对象存储；`create_client()` 无直接公开对位 |

### 4.2 vector / query

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `create_vector_store` | `StoreFactory.createVectorStore` | `chroma/milvus -> createVectorStore("chroma"/"milvus")` | 适配映射 | Java 额外支持 `in_memory`、`pgvector` |
| `VectorDataType` | `spi.store.vector.VectorDataType` | 枚举值一一对应 | 完全映射 | - |
| `FieldSchema` | `spi.store.vector.FieldSchema` | `to_dict/from_dict -> toDict/fromDict` | 完全映射 | 第二轮确认：`default_value -> getDefaultValue/defaultValue()` 已补齐 |
| `CollectionSchema` | `spi.store.vector.CollectionSchema` | `add_field/remove_field/get_field/has_field/get_primary_key_field/get_vector_fields/to_dict/from_dict/from_fields -> addField/removeField/getField/hasField/getPrimaryKeyField/getVectorFields/toDict/fromDict/fromFields` | 完全映射 | - |
| `VectorSearchResult` | `spi.store.vector.VectorSearchResult` | 数据字段映射 | 完全映射 | - |
| `BaseVectorStore` | `spi.store.vector.BaseVectorStore` | `create_collection/delete_collection/collection_exists/get_schema/add_docs/search/delete_docs_by_ids/delete_docs_by_filters/list_collection_names/update_schema/update_collection_metadata/get_collection_metadata -> createCollection/deleteCollection/collectionExists/getSchema/addDocs/search/deleteDocsByIds/deleteDocsByFilters/listCollectionNames/updateSchema/updateCollectionMetadata/getCollectionMetadata` | 完全映射 | 第二轮确认：方法面已补齐 |
| `ChromaVectorStore` | `store.vector.ChromaVectorStore` + `AbstractRetrievalVectorStoreAdapter` | Python `BaseVectorStore` 主干方法由 Java 适配器继承提供；`get_all_documents -> getAllDocuments` | 部分映射 | Java 已有同名扩展方法，但 metadata/schema migration/list names 仍主要依赖适配器层缓存逻辑 |
| `MilvusVectorStore` | `store.vector.MilvusVectorStore` + `AbstractRetrievalVectorStoreAdapter` | Python `BaseVectorStore` 主干方法由 Java 适配器继承提供；`close -> close` | 部分映射 | `client` 属性无公开直达；metadata/schema migration 由适配器层简化处理 |
| `store.vector.utils` | `store.vector.VectorStoreUtils` | `convert_l2_squared/convert_cosine_similarity/convert_cosine_distance/convert_ip_similarity/convert_ip_distance/compute_new_schema/build_transform_func_for_operations -> convertL2Squared/convertCosineSimilarity/convertCosineDistance/convertIpSimilarity/convertIpDistance/computeNewSchema/buildTransformFuncForOperations` | 完全映射 | 第二轮确认：Java 已补齐 |
| `QueryLanguageDefinition` | `spi.store.query.QueryLanguageDefinition` | 数据结构 -> builder + `apply*` family | 适配映射 | Java 用 builder 组织各类 filter applicator |
| `QueryExpr` | `spi.store.query.QueryExpr` | `sanitize_str/to_expr -> sanitizeStr/toExpr`；运算符重载 `&/|/^/~ -> and/or/xor/not` | 适配映射 | Java 额外提供显式逻辑组合方法 |
| `CustomExpr/ComparisonExpr/RangeExpr/ArithmeticExpr/NullExpr/JSONExpr/ArrayExpr/LogicalExpr/MatchExpr` | `spi.store.query.*` 同名类 | `to_expr -> toExpr` | 完全映射 | - |
| `eq/ne/gt/lt/gte/lte/in_list/wildcard_match/is_null/is_not_null/json_key/array_index/filter_user/chain_filters` | `spi.store.query.QueryExpressions.*` | 顶层函数 -> 静态工厂方法 | 完全映射 | - |
| `register_database_query_language` | `QueryLanguageRegistry.register` | `register_database_query_language(name, definition) -> register(name, definition)` | 适配映射 | Python 有 `force`；Java `register()` 直接覆盖 |
| `chroma_query_func.py` / `milvus_query_func.py` | `ChromaQueryDialect.definition()` / `MilvusQueryDialect.definition()` | dialect 定义一一对应 | 完全映射 | 第二轮确认：Java 已补齐内建 dialect 定义 |

### 4.3 graph / vector_fields

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `GraphConfig` | `store.graph.GraphConfig` | 字段 1:1 | 适配映射 | Java 用 builder 替代 Pydantic validator；运行期校验强度低于 Python |
| `BM25Config` | `store.graph.BM25Config` | 数据字段映射 | 完全映射 | 第二轮确认：Java 已补齐 |
| `GraphStoreIndexConfig` | `store.graph.GraphStoreIndexConfig` | 数据字段映射 | 完全映射 | 第二轮确认：Java 已补齐 |
| `GraphStoreStorageConfig` | `store.graph.GraphStoreStorageConfig` | 数据字段映射 | 完全映射 | 第二轮确认：Java 已补齐 |
| `GraphStoreFactory` | `store.graph.GraphStoreFactory` | `register_backend/from_config -> registerBackend/fromConfig` | 适配映射 | Java 工厂与 Python 同职责，但默认 backend 注册/可用性仍需另看 FIXED 文档 |
| `GraphStore` | `store.graph.GraphStore` | `from_config/refresh/add_data/add_entity/add_relation/add_episode/is_empty/query/delete/search/attach_embedder/close -> fromConfig/refresh/addData/addEntity/addRelation/addEpisode/isEmpty/query/delete/search/attachEmbedder/close` | 部分映射 | 第二轮确认：接口已在，但 concrete backend 仍未真正接通 |
| `graph.utils.batched` | `store.graph.GraphUtils.batched` | `batched -> batched` | 完全映射 | Java 额外提供 strict/non-strict 两个重载 |
| `VectorField` | `store.vector_fields.VectorField` | `to_dict -> toDict` | 适配映射 | Python 的 `should_keep()` 语义在 Java 里收束到 `finalizeDict()` 和各子类 `toDict(stage)` |
| `ChromaVectorField` | `store.vector_fields.ChromaVectorField` | 字段与 `to_dict` 语义映射到 getter/setter + `toDict` | 适配映射 | Python `validate_kwargs()` 改为 setter 校验 |
| `MilvusVectorField` | `store.vector_fields.MilvusVectorField` | `validate_sq_construct/validate_sq_search/validate_pq_construct -> validateSqConstruct/validateSqSearch/validatePqConstruct` | 完全映射 | - |
| `MilvusFLAT` / `MilvusAUTO` / `MilvusSCANN` / `MilvusIVF` / `MilvusHNSW` | `MilvusFLAT` / `MilvusAUTO` / `MilvusSCANN` / `MilvusIVF` / `MilvusHNSW` | 各 index variant 一一对应；`validate_extra_args -> validate()` 或 setter 校验 | 适配映射 | 第二轮确认：Java 已补齐 Milvus variant family |
| `PGVectorField` | `store.vector_fields.PGVectorField` | 字段与 `to_dict` 语义映射到 getter/setter + `toDict` | 适配映射 | Python `validate_kwargs()` 改为 setter 校验 |
| `create_extra_construct_field` / `create_extra_search_field` | 无直接对位 | Pydantic `FieldInfo` helper 被 Java 字段类内联实现 | 适配映射 | 属于框架层门面差异，不是业务能力缺失 |
| Python `vector_fields` 无工厂 helper | `BaseVectorFields` / `ChromaFields` / `MilvusFields` / `PgFields` | `defaultSchema(...)` 为 Java 补充 helper | Java 扩展 | Java 新增默认 schema 便捷构造器，Python 无同名入口 |

## 5. tool

### 5.1 基础 Tool / LocalFunction

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Input` / `Output` | `Map<String,Object>` / `Object` | 类型别名 -> 具体方法签名类型 | 适配映射 | Java 不再单独暴露类型别名 |
| `ToolCard` | `ToolCard` | `tool_info -> toolInfo` | 适配映射 | Python `input_params` 可是 `BaseModel` 类型；Java 固定为 JSON Schema `Map` |
| `Tool` | `Tool` | `card/invoke/stream -> getCard/invoke/stream` | 适配映射 | Java 提供无 `kwargs` 的便捷重载 |
| `ToolInfo` | `tool.schema.ToolInfo` | 数据字段映射 | 完全映射 | - |
| `McpToolInfo` | `tool.schema.McpToolInfo` | 数据字段映射 | 完全映射 | - |
| `tool()` 装饰器 | `@ToolDefinition` + `AnnotatedToolFactory.scan/fromMethod` | 装饰器声明 -> 注解 + 扫描工厂 | 适配映射 | Java 额外提供方法反射转 `LocalFunction` 的显式工厂 |
| `LocalFunction` | `tool.function.LocalFunction` | `invoke/stream -> invoke/stream` | 部分映射 | 主干能力可用，但 Python `support_args_param()` 与部分 `kwargs` 兼容未平移 |
| `support_args_param` | 无直接对位 | 无同名 helper | 缺失 | Java `LocalFunction` 只包装 `Function<Map<String,Object>, Object>` |

### 5.2 mcp

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `NO_TIMEOUT` | `McpServerConfig.NO_TIMEOUT` | 常量映射 | 完全映射 | - |
| `McpServerConfig` | `McpServerConfig` | 字段 1:1 | 完全映射 | `auth_headers/auth_query_params` 都保留 |
| `McpToolCard` | `McpToolCard` | `tool_info -> toolInfo` | 适配映射 | - |
| `MCPTool` | `McpTool` | `invoke/stream -> invoke/stream` | 适配映射 | Java 类名使用 `Mcp` 驼峰写法 |
| `McpClient` | `McpClient` | `connect/disconnect/list_tools/call_tool/get_tool_info -> connect/disconnect/listTools/callTool/getToolInfo` | 适配映射 | Java 额外统一要求 `getServerPath()` |
| `SseClient` | `tool.mcp.client.SseClient` | `connect/disconnect/list_tools/call_tool/get_tool_info` 由 `AbstractHttpMcpClient` 继承提供 | 部分映射 | Java SSE 是 HTTP JSON-RPC baseline 实现，不是 Python 那套 `mcp.client.sse` 会话对象 |
| `StdioClient` | `tool.mcp.client.StdioClient` | `connect/disconnect/list_tools/call_tool/get_tool_info -> connect/disconnect/listTools/callTool/getToolInfo` | 完全映射 | - |
| `StreamableHttpClient` | `tool.mcp.client.StreamableHttpClient` | `connect/disconnect/list_tools/call_tool/get_tool_info` 由 `AbstractHttpMcpClient` 继承提供 | 部分映射 | Java 侧同样是 HTTP JSON-RPC baseline 实现 |
| `PlaywrightClient` | `tool.mcp.client.PlaywrightClient` | `connect/disconnect/list_tools/call_tool/get_tool_info -> connect/disconnect/listTools/callTool/getToolInfo` | 完全映射 | Java 通过 `SseClient` / `StdioClient` 代理 |
| `AuthHeaderAndQueryProvider` | `AbstractHttpMcpClient` + `McpServerConfig.authHeaders/authQueryParams` | Python 独立 auth provider -> Java 内聚到抽象 HTTP MCP client | 适配映射 | 语义保留，不再单独暴露类 |
| `OpenApiClient` | `tool.mcp.client.OpenApiClient` | `connect/disconnect/list_tools/call_tool/get_tool_info -> connect/disconnect/listTools/callTool/getToolInfo` | 部分映射 | Java 能读 JSON/YAML spec 并产出工具卡，但入参 schema 与路由细节明显简化 |
| `ToolManager` | 无直接对位 | 工具管理折叠到 Java `OpenApiClient.operations` | 部分映射 | Python 有独立管理类，Java 无公开同名 API |
| `load_conf` | 无直接对位 | spec 读取逻辑内嵌到 `OpenApiClient.loadSpec()` | 部分映射 | Java 无公开 `load_conf()` helper |

### 5.3 service_api / schema extractor

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `APIParamLocation` | `ApiParamLocation` | 枚举值映射 | 适配映射 | Java 额外提供 `fromString()` |
| `APIParamMapper` | `ApiParamMapper` | `map -> map` | 完全映射 | - |
| `RestfulApiCard` | `RestfulApiCard` | URL/method/headers/queries/paths/timeout/max_response_byte_size 等字段映射 | 适配映射 | Python `validate_method/validate_url` 在 Java 构造期显式校验 |
| `RestfulApi` | `RestfulApi` | `invoke/stream -> invoke/stream` | 部分映射 | Java 已支持 `timeout/max_response_byte_size/raise_for_status`，但不保留 Python `request_args/skip_none_value/skip_inputs_validate` |
| `BaseResponseParser` | `parser.BaseResponseParser` | `can_parse/parse -> canParse/parse` | 适配映射 | Java 额外把 `_decode_bytes/_extract_charset_from_content_type` 暴露成 protected helper |
| `BaseResponseDecompressor` | `parser.BaseResponseDecompressor` | `can_decompress/decompress -> canDecompress/decompress` | 适配映射 | - |
| `JsonResponseParser` / `TextResponseParser` | `parser.JsonResponseParser` / `parser.TextResponseParser` | `can_parse/parse -> canParse/parse` | 完全映射 | - |
| `GzipDecompressor` / `DeflateDecompressor` | `parser.GzipDecompressor` / `parser.DeflateDecompressor` | `can_decompress/decompress -> canDecompress/decompress` | 完全映射 | - |
| `ParserRegistry` | `parser.ParserRegistry` | `register/register_decompressor/parse -> register/registerDecompressor/parse` | 适配映射 | Python 实例化即含默认组件；Java 采用 `getInstance()` 单例 |
| `CallableSchemaExtractor` | `tool.utils.CallableSchemaExtractor` | `generate_schema/extract_function_description -> generateSchema/extractFunctionDescription` | 部分映射 | Java 缺少 Python 公开的 `get_type_schema/get_base_model_schema/get_enum_schema` 等 helper |
| `TypeSchemaExtractor` | `tool.utils.TypeSchemaExtractor` | Python `TypeSchemaExtractor` 家族 -> Java 统一 `extract(Type)` | 部分映射 | Java 合并了 extractor hierarchy，保留结果生成但减少可扩展节点 |
| `TypeSchemaExtractorRegistry` + `Simple/Optional/Union/List/Tuple/Dict/Set/BaseModel/Enum/TypeVar/ForwardRef/Literal` extractors | 无直接对位 | 无同名类层级 | 缺失 | Java 当前只有一个静态 `TypeSchemaExtractor.extract(Type)` |

## 6. Java 侧额外扩展

- `llm.model_clients.*Factory` 与 `DefaultModelClientFactories`：Python 没有独立工厂层。
- `store.vector.InMemoryVectorStore`、`store.vector.PGVectorStore`：Java 比 Python 多出的 provider 封装。
- `store.vector_fields.BaseVectorFields/ChromaFields/MilvusFields/PgFields.defaultSchema(...)`：Java 多出的默认 schema 便捷工厂。
- `tool.function.AnnotatedToolFactory`：Java 通过注解扫描直接产出 `LocalFunction`。
- `tool.mcp.client.AbstractHttpMcpClient`：Java 把 SSE/streamable-http 的共性抽到抽象基类。

## 7. 结论

- 第二轮源码复核后，`foundation` 在“接口面”上的对齐度比旧文档反映的要高很多，尤其是 `store.base_embedding`、`store.graph` 契约、`store.vector.utils`、`store.vector_fields`、`FieldSchema.defaultValue` 与内建 query dialect 定义都已经存在。
- 现在需要重点关注的不是“有没有类/方法”，而是少数模块是否已经从“只有门面/适配层”走到“后端行为完全对齐”。这部分已单独整理到 `docs/FIXED/foundation_fixed.md`。
