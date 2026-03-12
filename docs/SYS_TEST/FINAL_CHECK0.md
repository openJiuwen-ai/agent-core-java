# FINAL_CHECK0

## 检查范围与方法

- Python 基线：`agent-core-python/openjiuwen/core`
- Java 基线：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core`，以及 `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/spi/store`
- 检查方式：
  - 逐模块对照 Python/Java 源码目录与关键实现
  - 抽查旧转换报告中提到的问题，确认是否已在当前源码中修复
  - 执行 `mvn -q -DskipTests compile`，当前 Java 主工程可正常编译

说明：`agent-core-java/agent-core-java/docs` 下已有多份历史报告，但其中一部分结论已经过时。本报告只以当前源码状态为准。

## 总结

- Java 版当前已经不是“只有骨架”的状态，很多旧缺漏已经补齐。
- 但当前仍有一批确认存在的缺漏，主要集中在：
  - 安全 guardrail 层
  - foundation.tool 的便捷封装与 MCP 具体客户端
  - foundation.store 的具体实现层
  - legacy 兼容层
  - distributed runner
- 此外还有少数“文件已存在但语义弱化”的点，最典型的是 memory 向量 schema migration、REST tool 的 SSL/proxy 支持、日志 handler/filter 动态能力。

## A. 当前仍然缺失的模块或能力

### A1. 高优先级缺漏

#### 1. `security.guardrail` 整包缺失

- Python 侧存在：
  - `security/guardrail/backends.py`
  - `security/guardrail/builtin.py`
  - `security/guardrail/enums.py`
  - `security/guardrail/guardrail.py`
  - `security/guardrail/models.py`
- Java 侧现状：
  - 没有 `com.openjiuwen.core.security.guardrail` 对应包
  - 只有 `common/exception/GuardrailError.java`，没有 guardrail 框架本体
- 影响：
  - Java 版无法像 Python 一样基于 callback framework 注册 `UserInputGuardrail` 这类安全防护链路
  - backend / result model / risk level / builtin guardrail 全部缺位

#### 2. `foundation.tool` 的 `@tool` 装饰器和 schema 自动提取缺失

- Python 侧存在：
  - `foundation/tool/tool.py`
  - `foundation/tool/utils/callable_schema_extractor.py`
  - `foundation/tool/utils/type_schema_extractor.py`
- Java 侧现状：
  - 已有 `Tool`、`ToolCard`、`LocalFunction`
  - 但没有注解式/装饰器式工具声明，也没有签名到 schema 的自动提取器
- 影响：
  - Java 侧工具定义仍要手工构造 `ToolCard` 和 `inputParams`
  - Python 那套“函数签名 -> schema -> LocalFunction”的低成本接入方式没有迁移过来

#### 3. MCP 具体传输客户端族缺失

- Python 侧存在：
  - `foundation/tool/mcp/client/sse_client.py`
  - `foundation/tool/mcp/client/stdio_client.py`
  - `foundation/tool/mcp/client/openapi_client.py`
  - `foundation/tool/mcp/client/streamable_http_client.py`
  - `foundation/tool/mcp/client/playwright_client.py`
- Java 侧现状：
  - 只有 `McpClient` 接口、`McpTool`、`McpServerConfig`、`McpToolCard`
  - 没有任何 SSE / stdio / OpenAPI / Playwright / streamable HTTP 的 concrete client
- 影响：
  - Java 侧只是定义了 MCP 抽象协议
  - 不能像 Python 一样直接连具体 MCP server

#### 4. `foundation.store` 具体实现层大面积缺失

- Python 侧存在：
  - `foundation/store/kv/*`
  - `foundation/store/db/default_db_store.py`
  - `foundation/store/vector/*`
  - `foundation/store/vector_fields/*`
  - `foundation/store/graph/*`
  - `foundation/store/object/aioboto_storage_client.py`
  - `foundation/store/__init__.py::create_vector_store`
- Java 侧现状：
  - `com.openjiuwen.spi.store` 下只有抽象层、query 表达式和 vector schema
  - 没有与 Python 对齐的 concrete KV/DB/vector/graph/object store 层
  - 也没有 `create_vector_store()` 这类统一入口
- 影响：
  - Java 这一层目前更像 SPI 定义，不是 Python `foundation.store` 的等价替代
  - 上层模块只能依赖外部补实现，开箱即用能力明显不足

#### 5. `runner.drunner` 分布式运行子模块缺失

- Python 侧存在：
  - `runner/drunner/dmessage_queue/*`
  - `runner/drunner/remote_client/*`
  - `runner/drunner/server_adapter/*`
- Java 侧现状：
  - `runner` 下只有 `base`、`callback`、`mq`、`resourcemanager`
  - 没有 `drunner` 对应层
- 影响：
  - 远程 agent 调用、server adapter、reply topic subscription 这类分布式运行链路没有迁移完成

### A2. 中优先级缺漏

#### 6. `controller.legacy` 整包缺失

- Python 侧存在：
  - `controller/legacy/controller.py`
  - `controller/legacy/intent_detection_controller.py`
  - `controller/legacy/reasoner/*`
  - `controller/legacy/task/*`
  - `controller/legacy/event/*`
  - `controller/legacy/config/reasoner_config.py`
- Java 侧现状：
  - 没有 `com.openjiuwen.core.controller.legacy`
- 影响：
  - 旧版 controller 兼容层整体未迁移
  - `IntentDetectionController`、legacy reasoner / planner / task / event API 都不能直接使用

#### 7. `single_agent.legacy` 整包缺失

- Python 侧存在：
  - `single_agent/legacy/agent.py`
  - `single_agent/legacy/config.py`
  - `single_agent/legacy/react_agent.py`
  - `single_agent/legacy/schema.py`
- Java 侧现状：
  - 没有 `com.openjiuwen.core.singleagent.legacy`
- 影响：
  - 旧版 single agent API、配置与 schema 兼容层未完成

#### 8. LLM 的 Markdown parser 与 InferenceAffinity 专用封装缺失

- Python 侧存在：
  - `foundation/llm/output_parsers/markdown_output_parser.py`
  - `foundation/llm/inference_affinity_model.py`
  - `foundation/llm/model_clients/inference_affinity_model_client.py`
- Java 侧现状：
  - `output_parsers` 只有 `BaseOutputParser` 和 `JsonOutputParser`
  - 没有 `MarkdownOutputParser`
  - 没有 `InferenceAffinityModel` / `InferenceAffinityModelClient`
- 影响：
  - Markdown 结构化输出解析能力没有迁移
  - InferenceAffinity / affinity 风格模型调用入口缺失

## B. 当前已存在实现，但与 Python 仍不等价

#### 1. memory 向量 schema migration 在 Java 中仍是 no-op

- Python 侧：
  - `memory/migration/migrator/vector_migrator.py` 会真正执行
    - `list_collection_names`
    - `get_collection_metadata`
    - `update_schema`
    - `update_collection_metadata`
- Java 侧：
  - `memory/migration/migrator/VectorMigrator.java` 明确写着
    - “logged but not fully executable”
    - `tryMigrate()` 记录 warning 后直接返回 `true`
- 根因：
  - Java `spi.store.vector.BaseVectorStore` 不暴露上述 schema 演进 API
- 影响：
  - memory 模块的向量 schema 演进在 Java 侧不会真正执行
  - 这不是“精简实现”，而是实质性能力缺口

#### 2. `RestfulApi` 缺少 Python 的 SSL / proxy 处理链路

- Python 侧：
  - `foundation/tool/service_api/restful_api.py` 使用
    - `SslUtils.get_ssl_config(...)`
    - `SslUtils.create_strict_ssl_context(...)`
    - `UrlUtils.get_global_proxy_url(...)`
- Java 侧：
  - `foundation/tool/service_api/RestfulApi.java` 直接使用 `HttpClient.newBuilder()`
  - 只做 URL 校验，不处理全局代理、SSL 证书、自定义 verify 行为
- 影响：
  - 需要代理、证书、关闭校验等网络环境时，Java 行为无法与 Python 对齐

#### 3. `LoggerProtocol` 的 handler/filter/logger API 仍是空壳

- Python 侧：
  - `common/logging/protocol.py` 明确要求
    - `add_handler`
    - `remove_handler`
    - `add_filter`
    - `remove_filter`
    - `logger`
- Java 侧：
  - `common/logging/LoggerProtocol.java` 这些方法是 default no-op
  - `logger()` default 返回 `null`
  - `DefaultLogger` 与 `LazyLogger` 没有把这些接口补成真实实现
- 影响：
  - Java 侧虽然“接口名在”，但动态插拔 handler/filter、获取底层 logger 实际不可用

## C. 旧报告里提过，但当前源码已经补齐，不应再重复报缺

- `common/utils/MessageUtils` 已经有 Java 实现，不再缺失。
- `ContextEngine` 已在静态块中注册内置 processor：
  - `CurrentRoundCompressor`
  - `DialogueCompressor`
  - `RoundLevelCompressor`
  - `MessageOffloader`
  - `MessageSummaryOffloader`
- `foundation.llm.Model` 现在已有内建 factory 注册，不再是“只有 `BaseModelClient` 没有 concrete client”的状态。
- `retrieval` 中以下能力当前 Java 已存在：
  - `RerankerConfig`
  - `BaseRankConfig` / `RRFRankConfig` / `WeightedRankConfig`
  - `BaseCallback` / `TqdmCallback`
  - `StoreType`
  - `EmbeddingUtils.parseBase64Embedding()`
- `session.checkpointer.InMemoryCheckpointer` 现在已经接入真实 `InMemoryStore graphStore()`。
- `session.interaction.AgentInteraction` 当前会在交互中断前调用 `interruptAgentExecute(session)`。
- `sysop/SysOperationToolAdapter` 现在已通过反射分发到真实子操作方法，不再是占位返回字符串。

## D. 修复优先级建议

1. 先补 `security.guardrail`
2. 再补 `foundation.tool` 的 `@tool`/schema extractor，以及 MCP concrete clients
3. 再补 `foundation.store` 的 concrete store/factory/graph/vector_fields`
4. 之后修 `memory` 的向量 schema migration 真正落地
5. 再做 `runner.drunner`
6. 最后补 `controller.legacy`、`single_agent.legacy`、`MarkdownOutputParser`、`InferenceAffinityModel`

