# foundation 模块第二轮缺漏复核

## 复核范围

- Python 基线：`F:\oepnjiuwen\agent-core-python\openjiuwen\core\foundation`
- Java foundation：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\foundation`
- Java SPI 补充：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\spi`

## 第二轮修正结论

第二轮按源码重新核对后，以下项目已经不能再算“缺漏”，旧文档里的对应结论应视为已修正：

- `store.base_embedding.EmbeddingConfig`、`Embedding`
- `store.graph` 的配置模型、工厂、接口契约、`GraphUtils.batched`
- `store.vector.utils` 整组工具函数
- `store.vector_fields` 的 `VectorField` / `ChromaVectorField` / `Milvus*` / `PGVectorField` 家族
- `spi.store.vector.FieldSchema.defaultValue`
- `store.query` 的 `ChromaQueryDialect` / `MilvusQueryDialect` 定义与 `QueryDialectRegistration` 辅助类

第二轮真正暴露出来的问题，已经从“类/方法不存在”转为“接口虽然在，但默认行为、注册路径或能力深度还没有对齐 Python”。

## 第二轮仍存在的真实缺口

| 优先级 | 位置 | Python 基线 | Java 当前状态 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `store.graph` concrete backend | Python `GraphStoreFactory.register_backend()/from_config()` 可配合真实 backend 使用 | Java 虽然有 `GraphStore` 接口、`GraphStoreFactory`、`GraphConfig`，但仓内没有任何类实现 `com.openjiuwen.core.foundation.store.graph.GraphStore`，也没有任何 `GraphStoreFactory.registerBackend(...)` 调用；`InMemoryGraphStore` 只是 `core.graph.store.InMemoryStore` 的别名，并不实现 foundation `GraphStore` 契约 | `GraphStoreFactory.fromConfig()` 目前并不能像 Python 一样开箱即用地创建 foundation graph backend |
| `P1` | `store.vector.AbstractRetrievalVectorStoreAdapter` 的 metadata / migration / collection API | Python `ChromaVectorStore` / `MilvusVectorStore` 的 `list_collection_names()`、`update_schema()`、`update_collection_metadata()`、`get_collection_metadata()` 都是面向真实后端实现 | Java 这些方法虽然在 `spi.store.vector.BaseVectorStore` 与适配器里都存在，但 `listCollectionNames()` 只返回本地 schema cache，`updateSchema()` 只更新 cache，`updateCollectionMetadata()` 是空实现，`getCollectionMetadata()` 默认返回空 `Map` | 现在是“签名对齐、行为未对齐”，会直接影响 schema migration、metadata versioning 和 collection 枚举 |
| `P1` | `store.vector.AbstractRetrievalVectorStoreAdapter.search()` 语义 | Python `search(..., vector_field=...)` 会把 `vector_field` 传给具体 store，并返回完整 `VectorSearchResult.fields` | Java `search()` 没把 `vectorField` 往 retrieval 层继续传递，且结果只重组出 `id/text/metadata` 三类字段 | 多向量字段、自定义字段检索与完整文档回填能力都弱于 Python |
| `P1` | `store.query` built-in dialect 自动注册 | Python `store.query.__init__` 导入时自动注册 `milvus` / `chroma` | Java 只有 `QueryDialectRegistration.ensureRegistered()`，但仓内没有自动调用点 | Java 侧如果只按 Python 用法“导入后直接用”，`QueryLanguageRegistry.get()` 会报未注册 |
| `P1` | `tool.mcp.client.OpenApiClient` | Python 通过 `load_conf()`、`ToolManager`、FastMCP `OpenAPITool`、`flat_param_schema`、output schema 提取，把 OpenAPI 路由转成完整工具卡 | Java 只做了浅层 spec 解析：`inputParams` 固定成空 object，缺少公开的 `load_conf()` / `ToolManager`，`callTool()` 也只是 path placeholder 替换 + GET/POST body 分流 | OpenAPI 转 MCP Tool 只达到了“能调用”，没有达到 Python 的“参数 schema、输出 schema、配置加载”完整能力 |
| `P2` | `tool.utils` schema extractor 家族 | Python 公开 `CallableSchemaExtractor.get_type_schema()/get_base_model_schema()/get_enum_schema()`，以及 `TypeSchemaExtractorRegistry + Simple/Optional/Union/List/Tuple/Dict/Set/BaseModel/Enum/TypeVar/ForwardRef/Literal` extractors | Java 只有 `CallableSchemaExtractor.generateSchema()/extractFunctionDescription()` 和一个统一的 `TypeSchemaExtractor.extract(Type)` | 结果能生成，但缺少显式 helper API 与可插拔 extractor 链，扩展性明显下降 |
| `P2` | `tool.function.LocalFunction` | Python `support_args_param()` 支持 `*args` 风格包装，`invoke/stream` 支持 `skip_none_value`、`skip_inputs_validate` | Java `LocalFunction` 固定包装 `Function<Map<String,Object>, Object>`，输入校验直接走 `SchemaUtils.formatWithSchema()` | 迁移依赖 varargs 包装或宽松校验的本地工具时，需要改调用侧 |
| `P2` | `tool.service_api.RestfulApi` | Python `invoke()` 接收 `request_args`、`skip_none_value`、`skip_inputs_validate` 等执行期 `kwargs` | Java 只保留了 `timeout`、`max_response_byte_size`、`raise_for_status` 这几个主要参数 | 复杂 HTTP client 参数透传与输入裁剪能力比 Python 少一层 |
| `P2` | `llm.schema.ModelClientConfig` provider 归一化 | Python `validate_client_provider()` 会按 registry 做大小写归一化与合法性校验 | Java `ModelClientConfig` 只是存原始字符串；`Model.createModelClient()` 直接按精确 key 查工厂；`ProviderType.fromValue()` 没有接入 builder / factory 流程 | Python 里可接受的 `openai`、`dashscope` 等大小写变体，Java 侧可能直接失败 |
| `P2` | `store.kv.ShelveStore` | Python 有本地 `shelve` 持久化 KV 实现 | Java 无同职责 provider | 轻量本地持久化 KV 迁移点缺失 |
| `P2` | `store.object.AioBotoClient` | Python 有 `create_client()` 与 S3/OBS 风格对象存储 provider | Java 只有 `LocalObjectStorageClient` | 抽象接口已对齐，但云对象存储 provider 生态未对齐 |
| `P2` | `store.graph.GraphConfig` 运行期校验 | Python `check_extras()` 会校验 `extras`，`check_validity()` 会探测 URI/创建本地目录 | Java `GraphConfig.Builder` 只做字段非空和数值范围校验 | 一些 Python 会在配置阶段暴露的问题，Java 会延后到运行期才暴露 |

## 建议优先级

1. 先补 `store.graph` 的真正可用 backend 与默认注册链路。
2. 再补 `store.vector` 适配器层的真实 metadata / migration / collection / search 语义，不要再停留在 cache-only。
3. 然后补 `OpenApiClient` 的参数 schema / 输出 schema / 配置加载能力。
4. 最后补 `LocalFunction`、`RestfulApi`、schema extractor 这类“易用性与兼容性”缺口。

## 备注

- 本文件列的是“第二轮复核后仍真实存在的缺口”，不是第一轮旧清单的简单延续。
- 已经补齐的类、方法、数据模型，这里不再重复计为缺漏。
- 具体类与方法映射关系，见 `docs/Reflect/foundation.md`。
