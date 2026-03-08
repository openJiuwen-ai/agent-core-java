# L0-L2 Python/Java 转译检查报告

- 检查时间: 2026-03-07
- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core`
- Java 基线: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen`
- 检查范围:

| 层级 | 名称 | 模块 | 职责 |
|---|---|---|---|
| L0 | 基础层 | `common` | 日志、异常、常量、工具类 |
| L1 | 基础设施层 | `foundation`, `sys_operation` | LLM 客户端、工具、存储、系统操作 |
| L2 | 核心引擎层 | `context_engine`, `graph`, `session` | 上下文、图引擎、会话管理 |

## 1. 总体结论

| 模块 | 结论 | 说明 |
|---|---|---|
| `common` | 基本完成 | 主体类已转译，少量工具/日志 API 缺漏 |
| `foundation` | 部分完成 | 抽象层较完整，但 concrete 实现和若干关键 API 缺失较多 |
| `sys_operation` | 基本完成 | 本地模式对齐度较高；沙箱模式与 Python 一样仍是占位 |
| `context_engine` | 部分完成 | 核心类已到位，但内置 processor 注册机制缺失，默认不可直接用内置处理器 |
| `graph` | 基本完成 | Pregel、store、stream actor、可视化主体已转译，序列化语义有偏移 |
| `session` | 部分完成 | 主体类很多，但检查点、交互中断、会话 API 有实质性行为偏差 |

## 2. L0: `common`

### 2.1 模块/API 对照

| Python | Java | 对照结论 |
|---|---|---|
| `common/constants/constant.py` | `core/common/constants/Constant.java` | 已转译 |
| `common/constants/enums.py` | `core/common/constants/ControllerType.java`, `TaskType.java` | 已转译，枚举命名改为 Java 风格 |
| `common/exception/codes.py` | `core/common/exception/StatusCode.java` | 已转译 |
| `common/exception/errors.py` | `core/common/exception/BaseError.java` 及各子类 | 已转译，Java 额外细分了异常类型 |
| `common/exception/status_mapping.py` | `core/common/exception/StatusMapping.java` | 已转译 |
| `common/logging/protocol.py` | `core/common/logging/LoggerProtocol.java` | 部分转译 |
| `common/logging/manager.py` | `core/common/logging/LogManager.java` | 已转译 |
| `common/logging/events.py` | `core/common/logging/events/*` | 已转译 |
| `common/logging/default/*` | `core/common/logging/defaults/*` | 已转译 |
| `common/logging/utils.py` | `core/common/logging/LoggingUtils.java` | 部分转译 |
| `common/schema/card.py`, `param.py` | `core/common/schema/BaseCard.java`, `Param.java`, `ParamType.java` | 已转译 |
| `common/security/*` | `core/common/security/*` | 已转译 |
| `common/utils/dict_utils.py` | `core/common/utils/DictUtils.java` | 已转译 |
| `common/utils/hash_util.py` | `core/common/utils/HashUtil.java` | 已转译 |
| `common/utils/ip_utils.py` | `core/common/utils/IpUtils.java` | 已转译 |
| `common/utils/schema_utils.py` | `core/common/utils/SchemaUtils.java` | 已转译 |
| `common/utils/singleton.py` | `core/common/utils/SingletonSupport.java` | 已转译 |
| `common/utils/message_utils.py` | 无 | 缺失 |

### 2.2 确认问题

1. `common/utils/message_utils.py` 在 Java 侧无对应实现。
   影响: Python 侧关于消息去重、追加 user/assistant/tool/workflow message 的公共入口在 Java 中不存在，只能由调用方重复实现。

2. `LoggerProtocol` 缺少 Python 版的 handler/filter 相关 API。
   Python 有: `add_handler`, `remove_handler`, `add_filter`, `remove_filter`, `logger()`
   Java 只有: `debug/info/warning/error/critical/exception/log/setLevel/getConfig/reconfigure`
   影响: 依赖自定义 handler/filter 动态插拔的调用点无法等价迁移。

3. `LoggingUtils` 缺少 `normalize_and_validate_log_path()`。
   影响: Python 在日志路径层面做了 realpath/敏感路径校验；Java 侧这部分能力未保留。

## 3. L1: `foundation`

### 3.1 `foundation.llm` 对照

| Python | Java | 对照结论 |
|---|---|---|
| `foundation/llm/model.py` | `core/foundation/llm/Model.java` | 已转译 |
| `foundation/llm/model_clients/base_model_client.py` | `core/foundation/llm/model_clients/BaseModelClient.java` | 已转译 |
| `foundation/llm/schema/*` | `core/foundation/llm/schema/*` | 已转译 |
| `foundation/llm/output_parsers/output_parser.py` | `core/foundation/llm/output_parsers/BaseOutputParser.java` | 已转译 |
| `foundation/llm/output_parsers/json_output_parser.py` | `core/foundation/llm/output_parsers/JsonOutputParser.java` | 已转译 |
| `foundation/llm/output_parsers/markdown_output_parser.py` | 无 | 缺失 |
| `foundation/llm/inference_affinity_model.py` | 无 | 缺失 |
| `foundation/llm/model_clients/openai_model_client.py` | 无 | 缺失 |
| `foundation/llm/model_clients/dashscope_model_client.py` | 无 | 缺失 |
| `foundation/llm/model_clients/siliconflow_model_client.py` | 无 | 缺失 |
| `foundation/llm/model_clients/inference_affinity_model_client.py` | 无 | 缺失 |

#### 确认问题

1. Java 只有 `BaseModelClient`，没有任何内置 concrete client。
   影响: Python 默认可直接使用 `OpenAIModelClient` 等，Java 主工程无法直接连接任何模型提供方。

2. `Model.java` 依赖 `ServiceLoader` 或 `registerFactory()` 注入 `ModelClientFactory`，但主代码里没有对应 factory 实现，也没有 `META-INF/services` 注册。
   影响: 即使 `Model` API 存在，默认也会因为找不到 provider factory 而不可用。

3. `MarkdownOutputParser` 缺失。
   影响: Python 侧 markdown 结构化输出解析能力未保留。

### 3.2 `foundation.tool` 对照

| Python | Java | 对照结论 |
|---|---|---|
| `foundation/tool/base.py` | `core/foundation/tool/Tool.java`, `ToolCard.java` | 已转译 |
| `foundation/tool/function/function.py` | `core/foundation/tool/function/LocalFunction.java` | 部分转译 |
| `foundation/tool/schema.py` | `core/foundation/tool/schema/ToolInfo.java`, `McpToolInfo.java` | 已转译 |
| `foundation/tool/service_api/*` | `core/foundation/tool/service_api/*` | 已转译 |
| `foundation/tool/mcp/base.py` | `core/foundation/tool/mcp/McpServerConfig.java`, `McpToolCard.java`, `McpTool.java` | 已转译 |
| `foundation/tool/mcp/client/mcp_client.py` | `core/foundation/tool/mcp/McpClient.java` | 已转译 |
| `foundation/tool/mcp/client/openapi_client.py` | 无 | 缺失 |
| `foundation/tool/mcp/client/stdio_client.py` | 无 | 缺失 |
| `foundation/tool/mcp/client/sse_client.py` | 无 | 缺失 |
| `foundation/tool/mcp/client/streamable_http_client.py` | 无 | 缺失 |
| `foundation/tool/mcp/client/playwright_client.py` | 无 | 缺失 |
| `foundation/tool/tool.py` 的 `@tool` 装饰器 | 无 | 缺失 |
| `foundation/tool/utils/callable_schema_extractor.py` | 无 | 缺失 |
| `foundation/tool/utils/type_schema_extractor.py` | 无 | 缺失 |

#### 确认问题

1. `@tool` 装饰器和 schema extractor 整套能力缺失。
   影响: Python 可以通过函数签名自动生成 `ToolCard`/JSON Schema；Java 只能手工构造 `ToolCard` 和参数 schema。

2. `LocalFunction` 行为不等价。
   Python:
   - `invoke()` 会先按 `input_params` 做 `SchemaUtils.format_with_schema()`
   - `stream()` 仅允许 generator/async generator，否则抛错

   Java:
   - `invoke()` 不做 schema 格式化/校验
   - `stream()` 如果返回值不是 `Iterator/Iterable`，会自动包装成单元素 iterator

   影响: Java 会放宽输入校验，并把本应报错的非流式函数伪装成流式结果，语义与 Python 不一致。

3. `McpTool.invoke()` 未按 `inputParams` 做 schema 校验。
   影响: Python 中依赖 schema 约束的 MCP tool，Java 版会直接透传原始入参。

4. `RestfulApiCard` 丢失 Python 的 URL/method validator；`RestfulApi` 丢失输入 schema 校验、SSL/proxy 配置处理。
   影响: Java 版 REST tool 在非法 URL/方法、代理、证书和入参格式方面更弱。

5. MCP transport client 族缺失。
   影响: 即使 `McpTool`/`McpClient` 接口存在，Java 主工程没有可直接使用的 SSE/stdio/OpenAPI/Playwright 客户端实现。

### 3.3 `foundation.store` 对照

Java 版该层被拆到 `com.openjiuwen.spi.store`。

| Python | Java | 对照结论 |
|---|---|---|
| `foundation/store/base_kv_store.py` | `spi/store/BaseKVStore.java`, `KVStorePipeline.java` | 已转译 |
| `foundation/store/base_db_store.py` | `spi/store/BaseDbStore.java` | 已转译 |
| `foundation/store/base_vector_store.py` | `spi/store/vector/*` | 已转译 |
| `foundation/store/query/*` | `spi/store/query/*` | 已转译 |
| `foundation/store/object/base_storage_client.py` | `spi/store/object/BaseObjectStorageClient.java` | 已转译 |
| `foundation/store/kv/in_memory_kv_store.py` | 无 | 缺失 |
| `foundation/store/kv/shelve_store.py` | 无 | 缺失 |
| `foundation/store/kv/db_based_kv_store.py` | 无 | 缺失 |
| `foundation/store/db/default_db_store.py` | 无 | 缺失 |
| `foundation/store/base_embedding.py` | 无 | 缺失 |
| `foundation/store/vector/chroma_vector_store.py` | 无 | 缺失 |
| `foundation/store/vector/milvus_vector_store.py` | 无 | 缺失 |
| `foundation/store/vector/utils.py` | 无 | 缺失 |
| `foundation/store/vector_fields/*` | 无 | 缺失 |
| `foundation/store/graph/base.py`, `config.py`, `database_config.py`, `graph_backend.py` | 无 | 缺失 |
| `foundation/store/__init__.py` 的 `create_vector_store()` | 无 | 缺失 |
| `foundation/store/object/aioboto_storage_client.py` | 无 | 缺失 |

#### 确认问题

1. Java 只保留了 store 抽象层和 query 表达式，绝大多数 concrete store 未转译。
   影响: L1 存储层目前更像 SPI 定义，无法替代 Python 现有的 KV/向量/对象存储能力。

2. `Embedding`/`EmbeddingConfig` 缺失。
   影响: foundation 层嵌入接口未保留，向量检索链路不完整。

3. `vector_fields`、`vector utils`、`graph store factory/config` 缺失。
   影响: schema 演化、索引配置、图数据库配置等 Python 侧配套 API 在 Java 侧不存在。

## 4. L1: `sys_operation`

### 4.1 模块/API 对照

| Python | Java | 对照结论 |
|---|---|---|
| `sys_operation/base.py` | `core/sysop/BaseOperation.java`, `OperationMode.java` | 已转译 |
| `sys_operation/shell.py`, `fs.py`, `code.py` | `BaseShellOperation.java`, `BaseFsOperation.java`, `BaseCodeOperation.java` | 已转译 |
| `sys_operation/config.py` | `core/sysop/config/*` | 已转译 |
| `sys_operation/result/*` | `core/sysop/result/*` | 已转译 |
| `sys_operation/local/*` | `core/sysop/local/*` | 已转译 |
| `sys_operation/registry.py` | `core/sysop/registry/*` | 已转译 |
| `sys_operation/sys_operation.py` | `core/sysop/SysOperation.java`, `SysOperationCard.java`, `ToolIdProxy.java` | 已转译 |
| `sys_operation/tool_adapter.py` | `core/sysop/SysOperationToolAdapter.java` | 已转译 |
| `sys_operation/sandbox/*` | `core/sysop/sandbox/*` | 已转译，占位程度与 Python 基本一致 |
| `sys_operation/sandbox/sandbox_gateway.py` | 无 | 缺失，占位类未保留 |

### 4.2 结论

- 本地模式 `local` 的壳层、文件、代码执行接口整体对齐度较高。
- 沙箱模式在 Python 原版里本身就是 `NotImplementedError`/`pass` 占位，因此 Java 这里虽然没有可用实现，但不算新增回归。
- `SandboxGateway`、`ContainerManager`、`Container`、`SandboxClient` 这几个 Python 占位类未转译，属于低优先级 API 缺漏。

## 5. L2: `context_engine`

### 5.1 模块/API 对照

| Python | Java | 对照结论 |
|---|---|---|
| `context_engine/base.py` | `core/context/ModelContext.java`, `ContextStats.java`, `ContextWindow.java` | 已转译 |
| `context_engine/context/context.py` | `core/context/context/SessionModelContext.java` | 已转译 |
| `context_engine/context/message_buffer.py` | `core/context/context/ContextMessageBuffer.java`, `OffloadMessageBuffer.java` | 已转译 |
| `context_engine/context/context_utils.py` | `core/context/context/ContextUtils.java` | 已转译 |
| `context_engine/context/kv_cache_manager.py` | `core/context/context/KVCacheManager.java` | 已转译 |
| `context_engine/context_engine.py` | `core/context/ContextEngine.java` | 已转译 |
| `context_engine/processor/base.py` | `core/context/processor/ContextProcessor.java`, `ContextEvent.java` | 已转译 |
| `context_engine/processor/compressor/*` | `core/context/processor/compressor/*` | 已转译 |
| `context_engine/processor/offloader/*` | `core/context/processor/offloader/*` | 已转译 |
| `context_engine/schema/config.py` | `core/context/schema/ContextEngineConfig.java` | 已转译 |
| `context_engine/schema/messages.py` | `core/context/schema/OffloadMixin.java`, `OffloadMessages.java` | 已转译 |
| `context_engine/token/base.py` | `core/context/token/TokenCounter.java` | 已转译 |
| `context_engine/token/tiktoken_counter.py` | `core/context/token/SimpleTokenCounter.java` | 语义弱化 |

### 5.2 确认问题

1. Java 主代码未注册任何内置 processor。
   Python 中以下类通过 `@ContextEngine.register_processor()` 自动注册:
   - `CurrentRoundCompressor`
   - `DialogueCompressor`
   - `RoundLevelCompressor`
   - `MessageOffloader`
   - `MessageSummaryOffloader`

   Java 主代码中没有对应静态注册；只有测试代码手工调用了 `ContextEngine.registerProcessor(...)`。
   影响: 运行时如果直接传入 `new ProcessorSpec("MessageOffloader", config)`，`ContextEngine.createContext()` 会因为找不到 processor type 而失败。

2. `TiktokenCounter` 被替换成启发式 `SimpleTokenCounter`。
   影响: context window、offload、compressor 的 token 预算会从“精确计数”退化为“按字符数估算”。

## 6. L2: `graph`

### 6.1 模块/API 对照

| Python | Java | 对照结论 |
|---|---|---|
| `graph/executable.py`, `graph/base.py` | `core/graph/Executable.java`, `ExecutableGraph.java`, `Graph.java` | 已转译 |
| `graph/graph.py` | `core/graph/PregelGraph.java`, `CompiledGraph.java` | 已转译 |
| `graph/atomic_node.py` | `core/graph/AtomicNode.java` | 已转译，异步/同步版本合并 |
| `graph/vertex.py` | `core/graph/Vertex.java` | 已转译 |
| `graph/pregel/*` | `core/graph/pregel/*` | 已转译 |
| `graph/store/base.py`, `inmemory.py` | `core/graph/store/*` | 已转译 |
| `graph/store/serde.py` | `core/graph/store/Serializer.java` | 部分转译 |
| `graph/stream_actor/*` | `core/graph/stream_actor/*` | 已转译 |
| `graph/visualization/*` | `core/graph/visualization/*` | 已转译 |
| `graph/graph_state.py` | `core/graph/GraphNodeState.java` | 已转译，类名调整 |

### 6.2 确认问题

1. `graph.store.serde` 的序列化语义发生变化。
   Python:
   - `create_serializer("pickle")` 返回 `PickleSerializer`
   - `create_serializer("json")` 明确抛错“不支持”

   Java:
   - 只支持 `"json"`
   - 没有 `PickleSerializer`

   影响: 如果上层假定 graph checkpoint/state blob 使用 Python 的 pickle 序列化，Java 不能兼容读取。

### 6.3 结论

- `graph` 主干能力已到位，属于 L2 中完成度较高的一层。
- 目前最大差异不是图执行本身，而是 store 序列化格式不兼容。

## 7. L2: `session`

### 7.1 模块/API 对照

| Python | Java | 对照结论 |
|---|---|---|
| `session/session.py` | `core/session/BaseSession.java`, `ProxySession.java`, `Session.java` | 已转译 |
| `session/agent.py` | `core/session/AgentSessionApi.java` | 部分转译 |
| `session/workflow.py` | `core/session/WorkflowSessionApi.java` | 已转译 |
| `session/node.py` | `core/session/NodeSessionApi.java` | 已转译 |
| `session/config/base.py` | `core/session/config/Config.java` | 部分转译 |
| `session/store.py` | `core/session/store/*` | 已转译 |
| `session/callback/*` | `core/session/callback/*` | 已转译 |
| `session/checkpointer/base.py`, `checkpointer.py`, `inmemory.py` | `core/session/checkpointer/*` | 已转译 |
| `session/checkpointer/persistence.py` | 无 | 缺失 |
| `session/internal/*` | `core/session/internal/*` | 部分转译 |
| `session/state/*` | `core/session/state/*` | 已转译 |
| `session/interaction/*` | `core/session/interaction/*` | 部分转译 |
| `session/stream/*` | `core/session/stream/*` | 已转译，但 API 形态有变化 |
| `session/tracer/*` | `core/session/tracer/*` | 已转译 |
| `session/agent_group.py` | 无 | 缺失 |

### 7.2 确认问题

1. `PersistenceCheckpointer` 整块缺失，`graphStore()` 仍是占位。
   Python 有:
   - `PersistenceCheckpointer`
   - `PersistenceCheckpointerProvider`
   - 持久化 `AgentStorage` / `WorkflowStorage`
   - graph store 读写

   Java:
   - 只有 `InMemoryCheckpointer`
   - `Checkpointer.graphStore()` 返回 `Object`
   - `InMemoryCheckpointer.graphStore()` 也是 placeholder `Object`

   影响: Java 版没有持久化会话/图检查点能力，中断恢复能力不完整。

2. `Checkpointer.getThreadId()` 及内部 workflow 维度处理不等价。
   Python: `session_id:workflow_id`
   Java: 内部 `getWorkflowId(session)` 直接回退为 `session.sessionId()`
   影响: 多 workflow/子 workflow 场景下 thread id 会退化，可能造成检查点键冲突。

3. `NodeSession.nodeConfig()` 直接返回 `null`。
   Python 会从 `workflow_config.spec.comp_configs[node_id]` 读取节点配置。
   影响: 依赖 node config 的组件在 Java 中无法通过 session 取到配置。

4. `WorkflowInteraction` 没有保留 Python 的 `GraphInterrupt/Interrupt` 语义。
   Python:
   - 从 workflow state 读取/清空 `INTERACTIVE_INPUT`
   - 提交 `commit_cmp()`
   - 抛出 `GraphInterrupt((Interrupt(...),))`

   Java:
   - 读取的是通用 state key
   - `nodeId` 退化为 `sessionId`
   - 抛出通用 `RuntimeException("GraphInterrupt: ...")`

   影响: 交互式暂停/恢复只是“字符串占位”，图引擎层无法获得 Python 等价的可恢复中断对象。

5. `AgentSession.agentId()` 在仅传 card 的情况下可能返回 `null`。
   Python `Session.get_agent_id()` 直接取 `self._card.id`
   Java `AgentSession.agentId()` 只优先看 `config.agentConfig`，card 仅作为 opaque object 保存，没有读取 card.id
   影响: `AgentSessionApi.getAgentId()` 在常见场景下会丢失 agent id。

6. `AgentSessionApi` 缺少 Python 的 `get_agent_name()` / `get_agent_description()`。
   影响: Python 用户态可以拿到 agent card 元信息，Java 同层 API 丢失。

7. `AgentSessionApi.streamIterator()` 不再是“流式迭代器”。
   Python: 返回 `AsyncIterator`
   Java: 直接调用 `collectStreamOutput()`，返回的是阻塞收集后的完整列表
   影响: 名称仍叫 `streamIterator`，但实际已失去增量消费语义。

8. `Config` 丢失 `workflow_session_vars` 这条 contextvars 覆盖链路。
   影响: Python 支持在上下文变量中覆盖 workflow 运行参数；Java 只能读系统环境变量和显式 `envs`。

9. `CallbackManager.trigger()` 行为比 Python 更弱。
   Python: handler 执行失败会向上抛出
   Java: 记录日志后吞掉异常
   影响: 回调链路中的失败不会按 Python 语义中断调用方。

## 8. 建议优先级

### P0

1. 为 `foundation.llm` 补齐至少一个可直接使用的内置 model client，并完成 `ModelClientFactory` 注册。
2. 为 `context_engine` 的内置 processors 增加主代码级自动注册，否则 Java 版默认无法使用压缩/卸载处理器。
3. 修正 `session` 的检查点与交互中断链路:
   - `graphStore()` 不再返回 placeholder
   - 使用真实 `workflowId`
   - `WorkflowInteraction` 返回/抛出图引擎可恢复的中断对象

### P1

1. 补齐 `foundation.store` concrete 实现，至少覆盖 Python 当前在用的 KV store、DB store、vector store。
2. 修正 `LocalFunction.stream()` 语义，并补上 `LocalFunction` / `McpTool` / `RestfulApi` 的输入 schema 校验。
3. 修正 `AgentSession.agentId()`、补齐 `getAgentName()` / `getAgentDescription()`、恢复真正的流式 `streamIterator()`。

### P2

1. 补齐 `common.utils.message_utils`、`common.logging.LoggingUtils.normalize_and_validate_log_path`。
2. 补齐 `MarkdownOutputParser`、MCP transport clients、`agent_group` API。
3. 评估 `graph` 序列化格式是否需要兼容 Python 的 pickle checkpoint。

## 9. 最终判断

- `common`、`sys_operation`、`graph` 的主体骨架已经比较接近 Python 版。
- 当前最大问题集中在 `foundation` 和 `session`:
  - `foundation` 更像“接口层已转过去，默认实现大面积缺失”
  - `session` 则是“类很多，但检查点/中断/交互/流式这些关键行为仍有明显语义偏差”
- 如果目标是“Java 版可直接替代 Python 版的 L0-L2 能力”，当前状态还不能判定为完整转译完成。
